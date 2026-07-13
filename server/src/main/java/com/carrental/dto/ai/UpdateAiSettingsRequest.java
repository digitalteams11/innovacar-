package com.carrental.dto.ai;

import lombok.Data;

@Data
public class UpdateAiSettingsRequest {
    private Boolean globalEnabled;
    private Long fallbackProviderId;
    private Long fallbackModelId;
    private Boolean fallbackEnabled;
    private Double temperature;
    private Integer maxOutputTokens;
    private Integer requestTimeoutSeconds;
    private Integer maxRetries;
    private String systemPrompt;
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
