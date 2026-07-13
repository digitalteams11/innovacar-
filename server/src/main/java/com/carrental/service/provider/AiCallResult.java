package com.carrental.service.provider;

public record AiCallResult(
        boolean success,
        String text,
        Long inputTokens,
        Long outputTokens,
        String errorCode,
        String message,
        Long latencyMs,
        Integer httpStatus,
        String safeProviderMessage
) {
    public static AiCallResult ok(String text, Long inputTokens, Long outputTokens, long latencyMs) {
        return new AiCallResult(true, text, inputTokens, outputTokens, null, null, latencyMs, null, null);
    }

    public static AiCallResult fail(String errorCode, String message) {
        return new AiCallResult(false, null, null, null, errorCode, message, null, null, null);
    }

    public static AiCallResult fail(String errorCode, String message, long latencyMs) {
        return new AiCallResult(false, null, null, null, errorCode, message, latencyMs, null, null);
    }

    public static AiCallResult fail(String errorCode, String message, long latencyMs,
                                     Integer httpStatus, String safeProviderMessage) {
        return new AiCallResult(false, null, null, null, errorCode, message, latencyMs, httpStatus, safeProviderMessage);
    }
}
