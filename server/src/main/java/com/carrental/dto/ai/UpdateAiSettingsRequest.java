package com.carrental.dto.ai;

import lombok.Data;

/**
 * Request body for {@code PUT /api/super-admin/ai/settings}. {@code apiKey}
 * is optional — when blank/null the existing encrypted key is left
 * untouched, so the frontend never needs to (and never can) read back the
 * real key to "preserve" it across a save.
 */
@Data
public class UpdateAiSettingsRequest {
    private Boolean enabled;
    private String apiKey;
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
}
