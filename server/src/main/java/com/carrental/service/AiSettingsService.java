package com.carrental.service;

import com.carrental.dto.ai.AiSettingsDto;
import com.carrental.dto.ai.UpdateAiSettingsRequest;
import com.carrental.entity.AiSettings;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Super Admin-only CRUD for the platform's Gemini configuration. The raw API
 * key never leaves this layer: it is encrypted before being persisted and
 * only ever exposed back to callers as a boolean + fixed mask.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiSettingsRepository aiSettingsRepository;
    private final EncryptionUtil encryptionUtil;
    private final GeminiClientService geminiClientService;
    private final AiAuditService aiAuditService;

    @Transactional(readOnly = true)
    public AiSettingsDto getSettings() {
        return toDto(loadOrCreate());
    }

    @Transactional
    public AiSettingsDto updateSettings(UpdateAiSettingsRequest request) {
        AiSettings settings = loadOrCreate();

        if (request.getEnabled() != null) settings.setEnabled(request.getEnabled());
        if ("**CLEAR**".equals(request.getApiKey())) {
            settings.setApiKeyEncrypted(null);
        } else if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            settings.setApiKeyEncrypted(encryptionUtil.encrypt(request.getApiKey().trim()));
        }
        if (request.getTextModel() != null && !request.getTextModel().isBlank()) settings.setTextModel(request.getTextModel().trim());
        if (request.getVisionModel() != null && !request.getVisionModel().isBlank()) settings.setVisionModel(request.getVisionModel().trim());
        if (request.getTimeoutSeconds() != null) settings.setTimeoutSeconds(clampTimeout(request.getTimeoutSeconds()));
        if (request.getMaxTokens() != null) settings.setMaxTokens(clampMaxTokens(request.getMaxTokens()));
        if (request.getTemperature() != null) settings.setTemperature(clampTemperature(request.getTemperature()));
        if (request.getEnableChat() != null) settings.setEnableChat(request.getEnableChat());
        if (request.getEnableReports() != null) settings.setEnableReports(request.getEnableReports());
        if (request.getEnableTranslations() != null) settings.setEnableTranslations(request.getEnableTranslations());
        if (request.getEnableSupportAssistant() != null) settings.setEnableSupportAssistant(request.getEnableSupportAssistant());
        if (request.getEnableGuideGenerator() != null) settings.setEnableGuideGenerator(request.getEnableGuideGenerator());
        if (request.getEnableAutomationSuggestions() != null) settings.setEnableAutomationSuggestions(request.getEnableAutomationSuggestions());
        if (request.getEnableImageGeneration() != null) settings.setEnableImageGeneration(request.getEnableImageGeneration());
        if (request.getMonthlyTokenLimit() != null) settings.setMonthlyTokenLimit(Math.max(0, request.getMonthlyTokenLimit()));
        if (request.getDailyRequestLimit() != null) settings.setDailyRequestLimit(Math.max(0, request.getDailyRequestLimit()));
        if (request.getAuditAllActions() != null) settings.setAuditAllActions(request.getAuditAllActions());

        AiSettings saved = aiSettingsRepository.save(settings);

        GeminiClientService.KeyResult keyAfterSave = geminiClientService.resolveKey(saved);
        log.info("[AI_SETTINGS_SAVE_DEBUG] settingsId={} provider={} enabled={} chatEnabled={} incomingApiKeyPresent={} encryptedKeyLength={} decryptCheckSuccess={} apiKeyConfigured={}",
                saved.getId(), saved.getProvider(), saved.getEnabled(), saved.getEnableChat(),
                request.getApiKey() != null && !request.getApiKey().isBlank() && !"**CLEAR**".equals(request.getApiKey()),
                saved.getApiKeyEncrypted() != null ? saved.getApiKeyEncrypted().length() : 0,
                keyAfterSave.isUsable(),
                keyAfterSave.isUsable());

        aiAuditService.log("SETTINGS", "SETTINGS_UPDATE", null, null, null, "SUCCESS", null);
        return toDto(saved);
    }

    /**
     * Real connectivity check. It tries the configured model first, then tries
     * fallback models for model-not-found or model-specific quota failures.
     * If every provider call fails, the result stays a real failure.
     */
    @Transactional
    public TestResult testConnection(String tempApiKey) {
        AiSettings settings = loadOrCreate();
        String apiKey;
        boolean usingTempKey = false;

        if (tempApiKey != null && !tempApiKey.isBlank()) {
            apiKey = tempApiKey.trim();
            usingTempKey = true;
        } else {
            GeminiClientService.KeyResult keyResult = geminiClientService.resolveKey(settings);
            if (keyResult.isDecryptFailed()) {
                String errMsg = "Saved Gemini API key cannot be decrypted. Re-enter and save the API key.";
                updateLastTest(settings, false, errMsg, "AI_KEY_DECRYPTION_FAILED");
                return new TestResult(false, errMsg,
                        settings.getProvider() != null ? settings.getProvider() : "GEMINI",
                        settings.getTextModel(), null, "AI_KEY_DECRYPTION_FAILED",
                        null, null, null);
            }
            if (keyResult.isMissing()) {
                String errMsg = "No Gemini API key is configured. Enter and save one in Super Admin -> AI & Automation.";
                updateLastTest(settings, false, errMsg, "AI_API_KEY_MISSING");
                return new TestResult(false, errMsg,
                        settings.getProvider() != null ? settings.getProvider() : "GEMINI",
                        settings.getTextModel(), null, "AI_API_KEY_MISSING",
                        null, null, null);
            }
            apiKey = keyResult.apiKey();
        }

        String primaryModel = (settings.getTextModel() != null && !settings.getTextModel().isBlank())
                ? settings.getTextModel().trim() : "gemini-1.5-flash";
        log.info("[AI_TEST_DEBUG] provider=GEMINI model={} usingTempKey={} timeoutSeconds={}",
                primaryModel, usingTempKey, settings.getTimeoutSeconds());

        GeminiClientService.GeminiResult result = geminiClientService.testConnection(
                apiKey, primaryModel, settings.getTimeoutSeconds());

        String workingModel = primaryModel;
        String fallbackMessage = null;
        if (!result.success() && shouldTryFallbackModel(result.errorCode())) {
            String primaryError = result.errorCode();
            String[] fallbacks = { "gemini-1.5-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-pro" };
            for (String fb : fallbacks) {
                if (fb.equals(primaryModel)) continue;
                log.info("[AI_TEST_DEBUG] Primary model '{}' failed with {} - trying fallback '{}'",
                        primaryModel, primaryError, fb);
                GeminiClientService.GeminiResult fbResult = geminiClientService.testConnection(
                        apiKey, fb, settings.getTimeoutSeconds());
                if (fbResult.success()) {
                    result = fbResult;
                    workingModel = fb;
                    settings.setTextModel(fb);
                    settings.setVisionModel(fb);
                    fallbackMessage = fallbackMessage(primaryModel, fb, primaryError);
                    log.info("[AI_TEST_DEBUG] Fallback model '{}' succeeded", fb);
                    break;
                }
                log.info("[AI_TEST_DEBUG] Fallback model '{}' also failed: {}", fb, fbResult.errorCode());
            }
        }

        updateLastTest(settings, result.success(),
                result.success() ? (fallbackMessage != null ? fallbackMessage : "Connected successfully.") : result.message(),
                result.success() ? null : result.errorCode());

        aiAuditService.log("SETTINGS", "TEST_CONNECTION", workingModel, null, null,
                result.success() ? "SUCCESS" : "FAILED", result.errorCode());

        String finalMessage = result.success()
                ? (fallbackMessage != null ? fallbackMessage : "Gemini connection successful.")
                : result.message();

        return new TestResult(
                result.success(),
                finalMessage,
                settings.getProvider() != null ? settings.getProvider() : "GEMINI",
                workingModel,
                result.latencyMs(),
                result.success() ? null : result.errorCode(),
                result.httpStatus(),
                result.safeProviderMessage(),
                fallbackMessage
        );
    }

    private boolean shouldTryFallbackModel(String errorCode) {
        return "AI_MODEL_NOT_FOUND".equals(errorCode) || "AI_QUOTA_EXCEEDED".equals(errorCode);
    }

    private String fallbackMessage(String primaryModel, String fallbackModel, String primaryError) {
        if ("AI_QUOTA_EXCEEDED".equals(primaryError)) {
            return "Selected model '" + primaryModel + "' hit a Gemini quota limit, but fallback '" +
                    fallbackModel + "' works. The AI model setting was updated to '" + fallbackModel + "'.";
        }
        return "Selected model '" + primaryModel + "' was not found, but fallback '" +
                fallbackModel + "' works. The AI model setting was updated to '" + fallbackModel + "'.";
    }

    private void updateLastTest(AiSettings settings, boolean success, String message, String errorCode) {
        settings.setLastTestedAt(LocalDateTime.now());
        settings.setLastTestSuccess(success);
        settings.setLastTestMessage(message);
        settings.setLastTestErrorCode(errorCode);
        aiSettingsRepository.save(settings);
    }

    public record TestResult(
            boolean success,
            String message,
            String provider,
            String model,
            Long latencyMs,
            String errorCode,
            Integer httpStatus,
            String safeProviderMessage,
            String fallbackMessage
    ) {}

    private AiSettings loadOrCreate() {
        return geminiClientService.loadSettings();
    }

    private String decrypt(String encrypted) {
        try {
            return encryptionUtil.decrypt(encrypted);
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer clampTimeout(int value) {
        return Math.max(5, Math.min(120, value));
    }

    private Integer clampMaxTokens(int value) {
        return Math.max(64, Math.min(32_768, value));
    }

    private Double clampTemperature(double value) {
        return Math.max(0.0, Math.min(2.0, value));
    }

    private AiSettingsDto toDto(AiSettings settings) {
        GeminiClientService.KeyResult keyResult = geminiClientService.resolveKey(settings);
        boolean configured = keyResult.isUsable();
        String apiKeyStatus = switch (keyResult.status()) {
            case OK -> "CONFIGURED";
            case MISSING -> "MISSING";
            case DECRYPT_FAILED -> "DECRYPTION_FAILED";
            case EMPTY_AFTER_DECRYPT -> "EMPTY_AFTER_DECRYPT";
        };
        return AiSettingsDto.builder()
                .enabled(settings.getEnabled())
                .provider(settings.getProvider())
                .apiKeyConfigured(configured)
                .apiKeyStatus(apiKeyStatus)
                .apiKeyMasked(configured ? "************" : "")
                .textModel(settings.getTextModel() != null && !settings.getTextModel().isBlank()
                        ? settings.getTextModel() : "gemini-1.5-flash")
                .visionModel(settings.getVisionModel() != null && !settings.getVisionModel().isBlank()
                        ? settings.getVisionModel() : "gemini-1.5-flash")
                .timeoutSeconds(settings.getTimeoutSeconds())
                .maxTokens(settings.getMaxTokens())
                .temperature(settings.getTemperature())
                .enableChat(settings.getEnableChat())
                .enableReports(settings.getEnableReports())
                .enableTranslations(settings.getEnableTranslations())
                .enableSupportAssistant(settings.getEnableSupportAssistant())
                .enableGuideGenerator(settings.getEnableGuideGenerator())
                .enableAutomationSuggestions(settings.getEnableAutomationSuggestions())
                .enableImageGeneration(settings.getEnableImageGeneration())
                .monthlyTokenLimit(settings.getMonthlyTokenLimit())
                .dailyRequestLimit(settings.getDailyRequestLimit())
                .auditAllActions(settings.getAuditAllActions())
                .lastTestedAt(settings.getLastTestedAt())
                .lastTestSuccess(settings.getLastTestSuccess())
                .lastTestMessage(settings.getLastTestMessage())
                .lastTestErrorCode(settings.getLastTestErrorCode())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
