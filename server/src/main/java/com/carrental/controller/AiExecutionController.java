package com.carrental.controller;

import com.carrental.dto.ai.AiExecuteRequest;
import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.entity.AiAutomation;
import com.carrental.entity.User;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiAutomationRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.AiGatewayService;
import com.carrental.service.AiUsageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic automation execution path — proves the catalog mechanism works
 * end to end. In this phase only {@code CHAT_ASSISTANT} passes the "wired"
 * check; every other seeded automation code returns
 * {@code AI_AUTOMATION_NOT_WIRED} rather than silently succeeding.
 *
 * <p>{@code /api/ai/chat} remains the primary path the UI uses for chat —
 * see {@code AiAssistantService} javadoc for why.
 */
@RestController
@RequestMapping("/api/ai/execute")
@RequiredArgsConstructor
public class AiExecutionController {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final AiAutomationRepository aiAutomationRepository;
    private final AiGatewayService aiGatewayService;
    private final AiUsageLogService aiUsageLogService;

    @PostMapping("/{automationCode}")
    public ResponseEntity<Map<String, Object>> execute(
            @AuthenticationPrincipal User user,
            @PathVariable String automationCode,
            @RequestBody(required = false) AiExecuteRequest request) {

        AiAutomation automation = aiAutomationRepository.findByCode(automationCode)
                .orElseThrow(AiServiceException::automationNotFound);

        if (!Boolean.TRUE.equals(automation.getEnabled())) {
            throw AiServiceException.automationDisabled();
        }
        if (!Boolean.TRUE.equals(automation.getWired())) {
            throw AiServiceException.automationNotWired();
        }

        // agencyId is always derived from the authenticated user's tenant context,
        // never accepted from the request body — prevents cross-agency data leakage.
        Long tenantId = TenantContext.getCurrentTenantId();
        assertWithinRateLimit(user, tenantId);

        String systemInstruction = automation.getSystemPrompt() != null
                ? automation.getSystemPrompt() : "You are a helpful assistant.";
        String userPrompt = renderTemplate(automation.getUserPromptTemplate(), request);

        AiExecuteResponse response = aiGatewayService.execute(automation.getCode(), systemInstruction, userPrompt);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Automation executed successfully.",
                "data", response
        ));
    }

    private void assertWithinRateLimit(User user, Long tenantId) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long userCount = aiUsageLogService.countSince(user.getId(), since);
        if (userCount >= 200) {
            throw AiServiceException.rateLimited();
        }
        if (tenantId != null && aiUsageLogService.countForAgencySince(tenantId, since) >= 2000) {
            throw AiServiceException.rateLimited();
        }
    }

    /**
     * Substitutes {@code {{variableName}}} tokens with caller-supplied values
     * — plain string replacement, never {@code eval}. Unmatched variables are
     * left blank rather than throwing, so a partial payload degrades safely.
     */
    private String renderTemplate(String template, AiExecuteRequest request) {
        if (template == null || template.isBlank()) {
            return request != null && request.getUserMessage() != null ? request.getUserMessage() : "";
        }
        Map<String, String> variables = request != null && request.getVariables() != null
                ? request.getVariables() : Map.of();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
