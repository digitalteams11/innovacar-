package com.carrental.service.provider;

import com.carrental.entity.AiProviderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroqProviderClientTest {

    private final GroqProviderClient client = new GroqProviderClient();

    @Test
    void getProviderType_isGroq() {
        assertThat(client.getProviderType()).isEqualTo(AiProviderType.GROQ);
    }

    @Test
    void testConnection_missingApiKey_failsWithoutNetworkCall() {
        AiTestResult result = client.testConnection(null, GroqProviderClient.DEFAULT_BASE_URL, "llama-3.3-70b-versatile", 5);
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("AI_API_KEY_MISSING");
    }

    @Test
    void testConnection_unreachableHost_returnsNetworkErrorNotSuccess() {
        // Deliberately invalid host so the call fails locally without needing a live Groq key —
        // exercises the same network-exception classification path used for real timeouts.
        AiTestResult result = client.testConnection("fake-key", "https://groq-does-not-exist.invalid", "llama-3.3-70b-versatile", 3);
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isNotBlank();
    }

    @Test
    void fetchAvailableModels_noApiKey_returnsEmptyListNotException() {
        assertThat(client.fetchAvailableModels(null, GroqProviderClient.DEFAULT_BASE_URL)).isEmpty();
    }

    @Test
    void capabilities_matchGroqOpenAiCompatibleFeatureSet() {
        assertThat(client.supportsStreaming()).isTrue();
        assertThat(client.supportsJsonMode()).isTrue();
        assertThat(client.supportsToolCalling()).isTrue();
    }
}
