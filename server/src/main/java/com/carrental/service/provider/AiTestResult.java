package com.carrental.service.provider;

public record AiTestResult(
        boolean success,
        String provider,
        String model,
        Long latencyMs,
        String message,
        String errorCode,
        Integer httpStatus,
        String safeProviderMessage
) {
}
