package com.carrental.service;

import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.entity.AiSettings;
import com.carrental.entity.Role;
import com.carrental.entity.User;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiSettingsRepository;
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
 * <p>Only ever forwards a curated, sanitized context to the active AI
 * provider (via {@link AiGatewayService}): role, agency name, current
 * module, page route, and the user's own message. Never the raw database,
 * other agencies' data, or any secrets.
 *
 * <p>This is the {@code CHAT_ASSISTANT} automation — the only automation
 * wired to a real backend flow in this phase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    public static final String AUTOMATION_CODE = "CHAT_ASSISTANT";

    private final AiGatewayService aiGatewayService;
    private final AiSettingsRepository aiSettingsRepository;
    private final AiPromptSanitizer aiPromptSanitizer;
    private final AiUsageLogService aiUsageLogService;
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
        AiSettings settings = aiSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> aiSettingsRepository.save(AiSettings.builder().build()));

        if (!Boolean.TRUE.equals(settings.getGlobalEnabled())) {
            throw AiServiceException.disabled();
        }
        if (!Boolean.TRUE.equals(settings.getEnableChat())) {
            throw AiServiceException.chatDisabled();
        }
        assertPlanAllowsChat(user);
        assertWithinRateLimit(user, settings);

        String role = user.getRole() != null ? user.getRole().name() : "UNKNOWN";
        String agencyName = user.getTenant() != null ? user.getTenant().getName() : "N/A";
        String systemInstruction = aiKnowledgeService.buildSystemInstruction(role, agencyName, module, route, user.getLanguage());

        String sanitizedMessage = aiPromptSanitizer.sanitize(message);

        AiExecuteResponse result = aiGatewayService.execute(AUTOMATION_CODE, systemInstruction, sanitizedMessage);

        String resolvedConversationId = (conversationId != null && !conversationId.isBlank())
                ? conversationId : UUID.randomUUID().toString();

        List<Map<String, String>> actions = aiKnowledgeService.suggestedActions(module, result.getContent());
        List<String> sources = aiKnowledgeService.sources(module);

        return new ChatResult(result.getContent(), resolvedConversationId, actions, sources);
    }

    /**
     * Defense-in-depth behind {@code FeatureAccessInterceptor}, which already
     * blocks unentitled requests to {@code /api/ai/**} before this method is
     * ever reached. Deliberately sources its message from the same
     * {@link FeatureAccessService} call the interceptor uses instead of a
     * separately hardcoded string — two independent "AI Assistant isn't on
     * your plan" messages previously existed and could disagree, which made a
     * genuine entitlement bug (a tenant on a phantom, unseeded plan) look like
     * a hardcoded block instead of a data problem. Never call this to bypass
     * or loosen the interceptor's check — only to keep the messaging aligned.
     *
     * <p>Deliberately does NOT catch arbitrary {@link RuntimeException} here.
     * A previous version did, and collapsed any unrelated failure in this
     * lookup (a DB error, a bad cast, tenant resolution failing) into the
     * same "AI Assistant is not included in your plan" message — masking
     * real backend errors as a false entitlement block. Let anything other
     * than an actual disentitlement propagate as its own error.
     */
    private void assertPlanAllowsChat(User user) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (!featureAccessService.isEnabledForCurrentTenant("AI_ASSISTANT")) {
            Map<String, Object> info = featureAccessService.checkCurrentTenantFeature("AI_ASSISTANT");
            String featureName = info.get("name") != null ? info.get("name").toString() : "AI Assistant";
            throw AiServiceException.featureDisabled(featureName);
        }
    }

    private void assertWithinRateLimit(User user, AiSettings settings) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        int dailyLimit = settings.getDailyRequestLimit() != null ? settings.getDailyRequestLimit() : 200;
        if (dailyLimit <= 0) {
            return;
        }
        long userCount = aiUsageLogService.countSince(user.getId(), since);
        if (userCount >= dailyLimit) {
            throw AiServiceException.rateLimited();
        }
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            long agencyCount = aiUsageLogService.countForAgencySince(tenantId, since);
            if (agencyCount >= dailyLimit * 10L) {
                throw AiServiceException.rateLimited();
            }
        }
    }
}
