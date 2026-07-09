package com.carrental.service;

import com.carrental.entity.AiSettings;
import com.carrental.entity.Role;
import com.carrental.entity.User;
import com.carrental.exception.AiServiceException;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs {@code POST /api/ai/chat} — the general-purpose assistant available
 * to Super Admin / Agency Admin / permitted employees.
 *
 * <p>Only ever forwards a curated, sanitized context to Gemini: role,
 * agency name, current module, page route, and the user's own message.
 * Never the raw database, other agencies' data, or any secrets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final GeminiClientService geminiClientService;
    private final AiPromptSanitizer aiPromptSanitizer;
    private final AiAuditService aiAuditService;
    private final FeatureAccessService featureAccessService;
    private final AiKnowledgeService aiKnowledgeService;

    public record ChatResult(
            String reply,
            String conversationId,
            List<Map<String, String>> suggestedActions,
            List<String> sources
    ) {}

    @Transactional
    public ChatResult chat(User user, String module, String message, String route, String conversationId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A message is required.");
        }
        AiSettings settings = geminiClientService.loadSettings();
        GeminiClientService.KeyResult keyResult = geminiClientService.resolveKey(settings);

        log.info("[AI_CHAT_KEY_DEBUG] settingsFound=true enabled={} chatEnabled={} encryptedKeyPresent={} decryptSuccess={} provider={} model={}",
                settings.getEnabled(), settings.getEnableChat(),
                !keyResult.isMissing(),
                keyResult.isUsable(),
                settings.getProvider(), settings.getTextModel());

        // Each check throws a SPECIFIC error code so the frontend can show an exact reason.
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            throw AiServiceException.disabled();
        }
        // Key checks — must distinguish "not set" from "can't decrypt"
        if (keyResult.isDecryptFailed()) {
            throw AiServiceException.keyDecryptionFailed();
        }
        if (!keyResult.isUsable()) {
            throw AiServiceException.keyNotConfigured();
        }
        if (!Boolean.TRUE.equals(settings.getEnableChat())) {
            throw AiServiceException.chatDisabled();
        }
        assertPlanAllowsChat(user);
        assertWithinRateLimit(user, settings);

        String role = user.getRole() != null ? user.getRole().name() : "UNKNOWN";
        String agencyName = user.getTenant() != null ? user.getTenant().getName() : "N/A";
        String systemInstruction = aiKnowledgeService.buildSystemInstruction(role, agencyName, module, route);

        String sanitizedMessage = aiPromptSanitizer.sanitize(message);

        GeminiClientService.GeminiResult result = geminiClientService.generateText(systemInstruction, sanitizedMessage);
        aiAuditService.log("CHAT", module != null ? module : "GENERAL", settings.getTextModel(),
                estimateTokens(sanitizedMessage), estimateTokens(result.text()),
                result.success() ? "SUCCESS" : "FAILED", result.errorCode());

        if (!result.success()) {
            throw new AiServiceException(
                    result.message() != null ? result.message() : "AI service is unavailable. Please try again later.",
                    result.errorCode() != null ? result.errorCode() : "AI_SERVICE_UNAVAILABLE");
        }

        String resolvedConversationId = (conversationId != null && !conversationId.isBlank())
                ? conversationId : UUID.randomUUID().toString();

        List<Map<String, String>> actions = aiKnowledgeService.suggestedActions(module, result.text());
        List<String> sources = aiKnowledgeService.sources(module);

        return new ChatResult(result.text(), resolvedConversationId, actions, sources);
    }

    private void assertPlanAllowsChat(User user) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        try {
            if (!featureAccessService.isEnabledForCurrentTenant("AI_ASSISTANT")) {
                throw AiServiceException.featureDisabled("AI Assistant");
            }
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw AiServiceException.featureDisabled("AI Assistant");
        }
    }

    private void assertWithinRateLimit(User user, AiSettings settings) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        int dailyLimit = settings.getDailyRequestLimit() != null ? settings.getDailyRequestLimit() : 200;
        if (dailyLimit <= 0) {
            return;
        }
        long userCount = aiAuditService.countSince(user.getId(), since);
        if (userCount >= dailyLimit) {
            throw AiServiceException.rateLimited();
        }
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            long agencyCount = aiAuditService.countForAgencySince(tenantId, since);
            if (agencyCount >= dailyLimit * 10L) {
                throw AiServiceException.rateLimited();
            }
        }
    }

    private Integer estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, text.length() / 4);
    }
}
