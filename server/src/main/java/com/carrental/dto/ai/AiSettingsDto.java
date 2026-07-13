package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Global AI settings — cross-cutting flags/limits only. Provider/model/key
 * configuration lives on {@link AiProviderDto}/{@link AiModelDto}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingsDto {
    private Boolean globalEnabled;
    private Long activeProviderId;
    private String activeProviderName;
    private Long activeModelId;
    private String activeModelName;
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
