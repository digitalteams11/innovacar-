package com.carrental.service.provider;

import com.carrental.entity.AiProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.carrental.service.provider.AiProviderHttpSupport.*;

/**
 * Migrated 1:1 from the original {@code GeminiClientService} — same
 * domain-only-baseUrl WebClient construction (an empty baseUrl causes
 * "Host is not specified"; a bare model name would otherwise be parsed as a
 * URI scheme) and the same HTTP-status error classification.
 */
@Slf4j
@Component
public class GeminiProviderClient implements AiProviderClient {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String MODEL_PATH = "/v1beta/models/";

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.GEMINI;
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
            return new AiTestResult(false, "GEMINI", model, null,
                    "No Gemini API key provided.", "AI_API_KEY_MISSING", null, null);
        }
        String resolvedModel = (model == null || model.isBlank()) ? "gemini-1.5-flash" : model.trim();
        AiCallResult result = call(resolveBaseUrl(baseUrl), resolvedModel, apiKey,
                "You are a connectivity check assistant. Reply with exactly two words: Connection OK",
                "Reply with OK", timeoutSeconds, 64, 0.4);

        return new AiTestResult(result.success(), "GEMINI", resolvedModel, result.latencyMs(),
                result.success() ? "Connection successful" : result.message(),
                result.errorCode(), result.httpStatus(), result.safeProviderMessage());
    }

    @Override
    public List<AiModelInfo> fetchAvailableModels(String apiKey, String baseUrl) {
        // Gemini's ListModels API requires a separate call shape; not implemented in this phase.
        return Collections.emptyList();
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
    }

    private AiCallResult call(String baseUrl, String model, String apiKey, String systemInstruction, String userPrompt,
                               Integer timeoutSeconds, Integer maxTokens, Double temperature) {
        String resolvedModel = (model == null || model.isBlank()) ? "gemini-1.5-flash" : model.trim();
        int timeout = timeoutSeconds != null ? timeoutSeconds : 30;
        long start = System.currentTimeMillis();

        try {
            WebClient client = WebClient.builder().baseUrl(baseUrl).build();

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userPrompt))
                    )),
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", systemInstruction))
                    ),
                    "generationConfig", Map.of(
                            "maxOutputTokens", maxTokens != null ? maxTokens : 2048,
                            "temperature", temperature != null ? temperature : 0.4
                    )
            );

            Map<?, ?> response = client.post()
                    .uri(MODEL_PATH + "{model}:generateContent?key={key}", resolvedModel, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(timeout));

            long latencyMs = System.currentTimeMillis() - start;
            String text = extractText(response);
            if (text == null || text.isBlank()) {
                return AiCallResult.fail("AI_PROVIDER_EMPTY_RESPONSE",
                        "Gemini responded but returned no text. The model may have filtered the content.",
                        latencyMs, 200, null);
            }
            long inputTokens = Math.max(1, (systemInstruction.length() + userPrompt.length()) / 4);
            long outputTokens = Math.max(1, text.length() / 4);
            return AiCallResult.ok(text, inputTokens, outputTokens, latencyMs);

        } catch (WebClientResponseException ex) {
            long latencyMs = System.currentTimeMillis() - start;
            int httpStatus = ex.getStatusCode().value();
            String providerMsg = safeProviderMessage(ex);
            log.warn("[GEMINI_CALL] model={} httpStatus={} success=false latencyMs={}",
                    resolvedModel, httpStatus, latencyMs);

            return switch (httpStatus) {
                case 400 -> AiCallResult.fail("AI_BAD_REQUEST",
                        "Gemini rejected the request (HTTP 400). " + (providerMsg != null ? providerMsg : "Check the model name and request format."),
                        latencyMs, httpStatus, providerMsg);
                case 401 -> AiCallResult.fail("AI_API_KEY_INVALID",
                        "Gemini API key is invalid (HTTP 401).", latencyMs, httpStatus, providerMsg);
                case 403 -> AiCallResult.fail("AI_PROVIDER_AUTH_FORBIDDEN",
                        "Gemini access forbidden (HTTP 403). " + (providerMsg != null ? providerMsg : ""),
                        latencyMs, httpStatus, providerMsg);
                case 404 -> AiCallResult.fail("AI_MODEL_NOT_FOUND",
                        "Gemini model '" + resolvedModel + "' was not found (HTTP 404).", latencyMs, httpStatus, providerMsg);
                case 429 -> AiCallResult.fail("AI_QUOTA_EXCEEDED",
                        "Gemini rate limit or quota exceeded (HTTP 429).", latencyMs, httpStatus, providerMsg);
                case 503 -> AiCallResult.fail("AI_SERVICE_UNAVAILABLE",
                        "Gemini service is temporarily unavailable (HTTP 503).", latencyMs, httpStatus, providerMsg);
                default -> AiCallResult.fail("AI_PROVIDER_HTTP_ERROR",
                        "Gemini returned HTTP " + httpStatus + ".", latencyMs, httpStatus, providerMsg);
            };

        } catch (WebClientRequestException ex) {
            long latencyMs = System.currentTimeMillis() - start;
            Throwable cause = rootCause(ex);
            String errorCode = classifyNetworkException(cause);
            return AiCallResult.fail(errorCode, networkErrorMessage(errorCode, "Gemini", cause), latencyMs);

        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - start;
            if (isTimeoutCause(ex)) {
                return AiCallResult.fail("AI_PROVIDER_TIMEOUT",
                        "Gemini did not respond within " + timeout + " seconds.", latencyMs);
            }
            Throwable root = rootCause(ex);
            String errorCode = classifyNetworkException(root);
            return AiCallResult.fail(errorCode, networkErrorMessage(errorCode, "Gemini", root), latencyMs);
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
        Object cands = response.get("candidates");
        if (!(cands instanceof List<?> list) || list.isEmpty()) return null;
        if (!(list.get(0) instanceof Map<?, ?> cand)) return null;
        Object content = cand.get("content");
        if (!(content instanceof Map<?, ?> contentMap)) return null;
        Object parts = contentMap.get("parts");
        if (!(parts instanceof List<?> partList) || partList.isEmpty()) return null;
        if (!(partList.get(0) instanceof Map<?, ?> partMap)) return null;
        Object text = partMap.get("text");
        return text != null ? text.toString() : null;
    }
}
