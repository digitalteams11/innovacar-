package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Super Admin-facing AI settings. The API key is NEVER included raw —
 * only {@code apiKeyConfigured} (boolean) and a fixed mask are exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingsDto {
    private Boolean enabled;
    private String provider;
    private boolean apiKeyConfigured;
    /**
     * Human-readable status: MISSING | CONFIGURED | DECRYPTION_FAILED | EMPTY_AFTER_DECRYPT
     */
    private String apiKeyStatus;
    private String apiKeyMasked;
    private String textModel;
    private String visionModel;
    private Integer timeoutSeconds;
    private Integer maxTokens;
    private Double temperature;
    private Boolean enableChat;
    private Boolean enableReports;
    private Boolean enableTranslations;
    private Boolean enableSupportAssistant;
    private Boolean enableGuideGenerator;
    private Boolean enableAutomationSuggestions;
    private Boolean enableImageGeneration;
    private Long monthlyTokenLimit;
    private Integer dailyRequestLimit;
    private Boolean auditAllActions;
    private LocalDateTime lastTestedAt;
    private Boolean lastTestSuccess;
    private String lastTestMessage;
    private String lastTestErrorCode;
    private LocalDateTime updatedAt;
}
