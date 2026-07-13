package com.carrental.service.provider;

public record AiCallRequest(
        String apiKey,
        String baseUrl,
        String model,
        String systemInstruction,
        String userPrompt,
        Integer timeoutSeconds,
        Integer maxOutputTokens,
        Double temperature
) {
}
