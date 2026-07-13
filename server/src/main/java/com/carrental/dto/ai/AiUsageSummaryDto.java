package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageSummaryDto {
    private boolean globalEnabled;
    private String activeProviderName;
    private String activeProviderType;
    private String activeModel;
    private String connectionStatus;
    private long requestsToday;
    private long successfulToday;
    private long failedToday;
    private Double averageLatencyMs;
    private String mostUsedAutomation;
}
