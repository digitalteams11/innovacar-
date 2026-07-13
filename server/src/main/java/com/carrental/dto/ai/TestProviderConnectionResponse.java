package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestProviderConnectionResponse {
    private boolean success;
    private String provider;
    private String model;
    private Long latencyMs;
    private String message;
    private String errorCode;
}
