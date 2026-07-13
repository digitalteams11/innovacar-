package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAutomationDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String featureType;
    private Boolean enabled;
    /** True only if a real backend flow triggers this automation today. */
    private Boolean wired;
    private Long providerId;
    private Long modelId;
    private String systemPrompt;
    private String userPromptTemplate;
    private BigDecimal temperature;
    private Integer maxOutputTokens;
    private String allowedRoles;
}
