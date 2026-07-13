package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageLogDto {
    private Long id;
    private Long providerId;
    private Long modelId;
    private String automationCode;
    private Long agencyId;
    private Long userId;
    private String role;
    private String status;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal estimatedCost;
    private Long latencyMs;
    private String errorCode;
    private LocalDateTime createdAt;
}
