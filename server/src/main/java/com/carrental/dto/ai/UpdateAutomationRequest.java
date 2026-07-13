package com.carrental.dto.ai;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateAutomationRequest {
    private String name;
    private String description;
    private Boolean enabled;
    private Long providerId;
    private Long modelId;
    private String systemPrompt;
    private String userPromptTemplate;
    private BigDecimal temperature;
    private Integer maxOutputTokens;
    private String allowedRoles;
}
