package com.carrental.service.provider;

import com.carrental.entity.AiProviderType;

import java.util.List;

/** Strategy interface every concrete AI provider implementation must satisfy. */
public interface AiProviderClient {

    AiProviderType getProviderType();

    /** Never throws — always returns a normalized {@link AiCallResult}. */
    AiCallResult generate(AiCallRequest request);

    /** Sends a minimal request with a very small token limit. Never throws. */
    AiTestResult testConnection(String apiKey, String baseUrl, String model, int timeoutSeconds);

    /**
     * Best-effort model listing. Providers without a models-list API may
     * throw {@link UnsupportedOperationException}.
     */
    List<AiModelInfo> fetchAvailableModels(String apiKey, String baseUrl);

    default boolean supportsStreaming() {
        return false;
    }

    default boolean supportsJsonMode() {
        return false;
    }

    default boolean supportsToolCalling() {
        return false;
    }
}
