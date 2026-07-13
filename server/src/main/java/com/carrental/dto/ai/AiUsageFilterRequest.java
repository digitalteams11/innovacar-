package com.carrental.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiUsageFilterRequest {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long providerId;
    private Long modelId;
    private Long agencyId;
    private String status;
    private String automationCode;
}
