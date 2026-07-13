package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExecuteResponse {
    private boolean success;
    private String requestId;
    private String provider;
    private String model;
    private String content;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long latencyMs;
    private boolean fallbackUsed;
}
