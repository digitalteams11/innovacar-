package com.carrental.service.provider;

import com.carrental.entity.AiProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.carrental.service.provider.AiProviderHttpSupport.*;

/**
 * Groq via its OpenAI-compatible API. Default base URL
 * {@code https://api.groq.com/openai/v1}, overridable per-row.
 */
@Slf4j
@Component
public class GroqProviderClient implements AiProviderClient {

    public static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";
    public static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.GROQ;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean supportsJsonMode() {
        return true;
    }

    @Override
    public boolean supportsToolCalling() {
        return true;
    }

    @Override
    public AiCallResult generate(AiCallRequest request) {
        return call(resolveBaseUrl(request.baseUrl()), request.model(), request.apiKey(),
                request.systemInstruction(), request.userPrompt(),
                request.timeoutSeconds(), request.maxOutputTokens(), request.temperature());
    }

    @Override
    public AiTestResult testConnection(String apiKey, String baseUrl, String model, int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AiTestResult(false, "GROQ", model, null,
                    "No Groq API key provided.", "AI_API_KEY_MISSING", null, null);
        }
        String resolvedModel = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        AiCallResult result = call(resolveBaseUrl(baseUrl), resolvedModel, apiKey,
                "You are a connectivity check assistant. Reply with exactly two words: Connection OK",
                "Reply with OK", timeoutSeconds, 16, 0.0);

        return new AiTestResult(result.success(), "GROQ", resolvedModel, result.latencyMs(),
                result.success() ? "Connection successful" : result.message(),
                result.errorCode(), result.httpStatus(), result.safeProviderMessage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AiModelInfo> fetchAvailableModels(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            return Collections.emptyList();
        }
        try {
            WebClient client = WebClient.builder().baseUrl(resolveBaseUrl(baseUrl)).build();
            Map<?, ?> response = client.get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(15));
            List<AiModelInfo> models = new ArrayList<>();
            if (response != null && response.get("data") instanceof List<?> data) {
                for (Object o : data) {
                    if (o instanceof Map<?, ?> m) {
                        String id = String.valueOf(m.get("id"));
                        Object ctx = m.get("context_window");
                        Long contextWindow = ctx != null ? Long.valueOf(ctx.toString()) : null;
                        models.add(new AiModelInfo(id, id, contextWindow));
                    }
                }
            }
            return models;
        } catch (Exception ex) {
            log.warn("[GROQ_MODELS] Failed to fetch model list: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
    }

    private AiCallResult call(String baseUrl, String model, String apiKey, String systemInstruction, String userPrompt,
                               Integer timeoutSeconds, Integer maxTokens, Double temperature) {
        String resolvedModel = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        int timeout = timeoutSeconds != null ? timeoutSeconds : 30;
        long start = System.currentTimeMillis();

        try {
            WebClient client = WebClient.builder().baseUrl(baseUrl).build();

            Map<String, Object> body = Map.of(
                    "model", resolvedModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemInstruction),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", maxTokens != null ? maxTokens : 2048,
                    "temperature", temperature != null ? temperature : 0.4
            );

            Map<?, ?> response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(timeout));

            long latencyMs = System.currentTimeMillis() - start;
            String text = extractText(response);
            if (text == null || text.isBlank()) {
                return AiCallResult.fail("AI_PROVIDER_EMPTY_RESPONSE",
                        "Groq responded but returned no text.", latencyMs, 200, null);
            }
            long[] usage = extractUsage(response);
            return AiCallResult.ok(text, usage[0], usage[1], latencyMs);

        } catch (WebClientResponseException ex) {
            long latencyMs = System.currentTimeMillis() - start;
            int httpStatus = ex.getStatusCode().value();
            String providerMsg = safeProviderMessage(ex);
            log.warn("[GROQ_CALL] model={} httpStatus={} success=false latencyMs={}", resolvedModel, httpStatus, latencyMs);

            return switch (httpStatus) {
                case 400 -> AiCallResult.fail("AI_BAD_REQUEST",
                        "Groq rejected the request (HTTP 400). " + (providerMsg != null ? providerMsg : ""),
                        latencyMs, httpStatus, providerMsg);
                case 401 -> AiCallResult.fail("AI_INVALID_API_KEY",
                        "Groq API key is invalid (HTTP 401).", latencyMs, httpStatus, providerMsg);
                case 404 -> AiCallResult.fail("AI_MODEL_NOT_FOUND",
                        "Groq model '" + resolvedModel + "' was not found (HTTP 404).", latencyMs, httpStatus, providerMsg);
                case 429 -> AiCallResult.fail("AI_QUOTA_EXCEEDED",
                        "Groq rate limit or quota exceeded (HTTP 429).", latencyMs, httpStatus, providerMsg);
                case 502, 503 -> AiCallResult.fail("AI_SERVICE_UNAVAILABLE",
                        "Groq service is temporarily unavailable (HTTP " + httpStatus + ").", latencyMs, httpStatus, providerMsg);
                default -> AiCallResult.fail("AI_PROVIDER_HTTP_ERROR",
                        "Groq returned HTTP " + httpStatus + ".", latencyMs, httpStatus, providerMsg);
            };

        } catch (WebClientRequestException ex) {
            long latencyMs = System.currentTimeMillis() - start;
            Throwable cause = rootCause(ex);
            String errorCode = classifyNetworkException(cause);
            return AiCallResult.fail(errorCode, networkErrorMessage(errorCode, "Groq", cause), latencyMs);

        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - start;
            if (isTimeoutCause(ex)) {
                return AiCallResult.fail("AI_PROVIDER_TIMEOUT",
                        "Groq did not respond within " + timeout + " seconds.", latencyMs);
            }
            Throwable root = rootCause(ex);
            String errorCode = classifyNetworkException(root);
            return AiCallResult.fail(errorCode, networkErrorMessage(errorCode, "Groq", root), latencyMs);
        }
    }

    private static String safeProviderMessage(WebClientResponseException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            int idx = body.indexOf("\"message\":");
            if (idx < 0) return null;
            int start = body.indexOf('"', idx + 10) + 1;
            int end = body.indexOf('"', start);
            if (start > 0 && end > start) {
                String msg = body.substring(start, end);
                return msg.length() <= 300 ? msg : msg.substring(0, 300);
            }
        } catch (Exception ignore) {
            // never let logging crash the call
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        if (response == null) return null;
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return null;
        if (!(list.get(0) instanceof Map<?, ?> choice)) return null;
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) return null;
        Object content = messageMap.get("content");
        return content != null ? content.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private long[] extractUsage(Map<?, ?> response) {
        if (response != null && response.get("usage") instanceof Map<?, ?> usage) {
            long in = toLong(usage.get("prompt_tokens"));
            long out = toLong(usage.get("completion_tokens"));
            return new long[]{in, out};
        }
        return new long[]{0L, 0L};
    }

    private long toLong(Object o) {
        return o != null ? Long.parseLong(o.toString()) : 0L;
    }
}
