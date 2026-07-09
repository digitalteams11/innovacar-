package com.carrental.service;

import com.carrental.entity.AiSettings;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * The ONLY class in the codebase that talks to Gemini's HTTP API.
 *
 * <p><strong>Single source of truth:</strong> the {@code ai_settings} DB row.
 * The environment variable {@code GEMINI_API_KEY} is used only to seed the
 * row on first startup. After that, the DB row is the only source.
 *
 * <p>The API key is decrypted in memory only for the duration of the HTTP
 * call and never logged, returned, or echoed back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiClientService {

    /**
     * Domain-only base URL passed to WebClient.
     * MUST NOT be empty â€” an empty baseUrl causes "Host is not specified" at WebClient build time.
     */
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

    /**
     * Path prefix for the Generative Language API â€” appended to the base URL.
     * Starting with "/" makes it unambiguously a path, preventing Spring's URI parser
     * from interpreting "gemini-2.0-flash:generateContent" as a URI scheme.
     */
    private static final String GEMINI_MODEL_PATH = "/v1beta/models/";

    /** Full prefix kept for log messages. */
    private static final String API_BASE = GEMINI_BASE_URL + GEMINI_MODEL_PATH;

    private final AiSettingsRepository aiSettingsRepository;
    private final EncryptionUtil encryptionUtil;

    @Value("${app.ai.gemini.api-key:}")
    private String seedApiKey;

    @Value("${app.ai.gemini.model-text:gemini-1.5-flash}")
    private String seedModelText;

    @Value("${app.ai.gemini.model-vision:gemini-1.5-flash}")
    private String seedModelVision;

    @Value("${app.ai.gemini.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.ai.gemini.timeout-seconds:30}")
    private int seedTimeoutSeconds;

    @Value("${app.ai.gemini.max-tokens:4096}")
    private int seedMaxTokens;

    // â”€â”€ GeminiResult â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public record GeminiResult(
            boolean success,
            String text,
            String errorCode,
            String message,
            Long latencyMs,
            Integer httpStatus,
            String safeProviderMessage
    ) {
        public static GeminiResult ok(String text, long latencyMs) {
            return new GeminiResult(true, text, null, null, latencyMs, null, null);
        }

        public static GeminiResult fail(String errorCode, String message) {
            return new GeminiResult(false, null, errorCode, message, null, null, null);
        }

        public static GeminiResult fail(String errorCode, String message, long latencyMs) {
            return new GeminiResult(false, null, errorCode, message, latencyMs, null, null);
        }

        public static GeminiResult fail(String errorCode, String message, long latencyMs,
                                        Integer httpStatus, String safeProviderMessage) {
            return new GeminiResult(false, null, errorCode, message, latencyMs, httpStatus, safeProviderMessage);
        }
    }

    // â”€â”€ KeyResult â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Three-state result from resolving the stored API key.
     * Callers MUST NOT conflate "key not in DB" with "key can't be decrypted".
     */
    public static final class KeyResult {
        public enum Status { OK, MISSING, DECRYPT_FAILED, EMPTY_AFTER_DECRYPT }

        private final Status  status;
        private final String  apiKey;
        private final int     encryptedLength;

        private KeyResult(Status status, String apiKey, int encryptedLength) {
            this.status          = status;
            this.apiKey          = apiKey;
            this.encryptedLength = encryptedLength;
        }

        public static KeyResult ok(String key)          { return new KeyResult(Status.OK, key, 0); }
        public static KeyResult missing()               { return new KeyResult(Status.MISSING, null, 0); }
        public static KeyResult decryptFailed(int len)  { return new KeyResult(Status.DECRYPT_FAILED, null, len); }
        public static KeyResult emptyAfterDecrypt()     { return new KeyResult(Status.EMPTY_AFTER_DECRYPT, null, 0); }

        public Status  status()          { return status; }
        public boolean isUsable()        { return status == Status.OK; }
        public boolean isDecryptFailed() { return status == Status.DECRYPT_FAILED; }
        public boolean isMissing()       { return status == Status.MISSING || status == Status.EMPTY_AFTER_DECRYPT; }
        public String  apiKey()          { return apiKey; }
        public int     encryptedLength() { return encryptedLength; }
    }

    // â”€â”€ Settings loading â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * The canonical accessor for the singleton {@code ai_settings} row.
     * Seeds the row from environment variables only if it does not yet exist.
     */
    public AiSettings loadSettings() {
        return aiSettingsRepository.findAll().stream()
                .findFirst()
                .orElseGet(this::seedFromEnvironment);
    }

    private AiSettings seedFromEnvironment() {
        AiSettings.AiSettingsBuilder builder = AiSettings.builder()
                .enabled(seedEnabled)
                .textModel(seedModelText)
                .visionModel(seedModelVision)
                .timeoutSeconds(seedTimeoutSeconds)
                .maxTokens(seedMaxTokens);
        if (seedApiKey != null && !seedApiKey.isBlank()) {
            builder.apiKeyEncrypted(encryptionUtil.encrypt(seedApiKey.trim()));
            log.info("[AI_SETTINGS] Seeded Gemini API key from GEMINI_API_KEY env var on first startup.");
        } else {
            log.info("[AI_SETTINGS] No GEMINI_API_KEY env var â€” row created without API key. Configure via Super Admin â†’ AI & Automation.");
        }
        return aiSettingsRepository.save(builder.build());
    }

    /**
     * Resolves and decrypts the API key from a settings row.
     * Returns the appropriate {@link KeyResult} so callers can
     * distinguish missing, corrupted, and valid keys.
     */
    public KeyResult resolveKey(AiSettings settings) {
        String encrypted = settings.getApiKeyEncrypted();
        if (encrypted == null || encrypted.isBlank()) {
            return KeyResult.missing();
        }
        String plain = encryptionUtil.tryDecrypt(encrypted);
        if (plain == null) {
            return KeyResult.decryptFailed(encrypted.length());
        }
        if (plain.isBlank()) {
            return KeyResult.emptyAfterDecrypt();
        }
        return KeyResult.ok(plain);
    }

    // â”€â”€ Public entry points â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Sends a single-turn text prompt to Gemini. Never throws â€” returns a
     * normalized {@link GeminiResult} with exact error codes.
     */
    public GeminiResult generateText(String systemInstruction, String userPrompt) {
        AiSettings settings = loadSettings();
        KeyResult keyResult = resolveKey(settings);

        log.info("[AI_CHAT_KEY_DEBUG] settingsFound=true enabled={} chatEnabled={} encryptedKeyPresent={} " +
                        "decryptSuccess={} decryptedKeyBlank={} provider={} model={}",
                settings.getEnabled(), settings.getEnableChat(),
                keyResult.status() != KeyResult.Status.MISSING,
                keyResult.isUsable(),
                keyResult.status() == KeyResult.Status.EMPTY_AFTER_DECRYPT,
                settings.getProvider(), settings.getTextModel());

        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return GeminiResult.fail("AI_DISABLED", "AI features are currently disabled.");
        }

        switch (keyResult.status()) {
            case MISSING:
                return GeminiResult.fail("AI_KEY_NOT_CONFIGURED",
                        "Gemini API key is not configured. Enter and save one in Super Admin â†’ AI & Automation.");
            case DECRYPT_FAILED:
                return GeminiResult.fail("AI_KEY_DECRYPTION_FAILED",
                        "Saved Gemini API key cannot be decrypted. Re-enter and save the API key in AI & Automation settings.");
            case EMPTY_AFTER_DECRYPT:
                return GeminiResult.fail("AI_KEY_NOT_CONFIGURED",
                        "Stored Gemini API key decrypted to an empty string. Re-enter and save the API key.");
            default: // OK
                return call(settings.getTextModel(), keyResult.apiKey(), systemInstruction, userPrompt,
                        settings.getTimeoutSeconds(), settings.getMaxTokens(), settings.getTemperature());
        }
    }

    /**
     * Test endpoint â€” accepts an optional temporary raw key so the admin can
     * test before saving. Always makes a real Gemini call; never a fake success.
     *
     * <p>Logs safe debug info including the first 6 chars of the API key (prefix only â€” never full key).
     */
    public GeminiResult testConnection(String rawApiKey, String model, Integer timeoutSeconds) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return GeminiResult.fail("AI_API_KEY_MISSING",
                    "No Gemini API key provided. Enter and save one first.");
        }
        String resolvedModel = (model == null || model.isBlank()) ? "gemini-1.5-flash" : model.trim();
        int timeout = timeoutSeconds != null ? timeoutSeconds : 15;

        // Safe debug log â€” first 6 chars of key only, never the full key
        String apiKeyPrefix = rawApiKey.length() >= 6
                ? rawApiKey.substring(0, 6) + "..."
                : rawApiKey.substring(0, Math.min(2, rawApiKey.length())) + "***";

        log.info("[GEMINI_TEST_DEBUG] provider=GEMINI baseUrl={} model={} hasApiKey=true apiKeyPrefix={} " +
                        "endpoint={}:generateContent timeoutSeconds={} maxTokens=64 temperature=0.4",
                GEMINI_BASE_URL, resolvedModel, apiKeyPrefix, API_BASE + resolvedModel, timeout);

        GeminiResult result = call(
                resolvedModel,
                rawApiKey,
                "You are a connectivity check assistant. Reply with exactly two words: Connection OK",
                "Reply with OK",
                timeout,
                64,      // small token limit for test â€” not 4096
                0.4      // as specified in requirements
        );

        log.info("[GEMINI_TEST_DEBUG] model={} success={} errorCode={} httpStatus={} safeMessage={} durationMs={}",
                resolvedModel, result.success(), result.errorCode(),
                result.httpStatus(), result.safeProviderMessage(), result.latencyMs());

        return result;
    }

    // â”€â”€ Internal HTTP call â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private GeminiResult call(String model, String apiKey, String systemInstruction, String userPrompt,
                               Integer timeoutSeconds, Integer maxTokens, Double temperature) {
        String resolvedModel = (model == null || model.isBlank()) ? "gemini-1.5-flash" : model.trim();
        int timeout = timeoutSeconds != null ? timeoutSeconds : 30;
        long start = System.currentTimeMillis();

        log.debug("[AI_GEMINI_CALL_DEBUG] model={} apiKeyPresent={} apiKeyLength={} " +
                        "timeoutSeconds={} maxTokens={} temperature={}",
                resolvedModel,
                apiKey != null && !apiKey.isBlank(),
                apiKey != null ? apiKey.length() : 0,
                timeout, maxTokens, temperature);

        try {
            // Use domain-only baseUrl so the path URI template ("/v1beta/models/{model}:generateContent")
            // is unambiguously a path. If the model name were used as the start of the URI string
            // (e.g. "gemini-2.0-flash:generateContent"), Spring's URI parser treats "gemini-2.0-flash"
            // as a URI scheme and produces a host-less URI â†’ "Host is not specified" error.
            WebClient client = WebClient.builder().baseUrl(GEMINI_BASE_URL).build();

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

            // Path starts with "/" â€” safe relative path, never misinterpreted as a URI scheme.
            // Template variables {model} and {key} are expanded positionally by WebClient.
            Map<?, ?> response = client.post()
                    .uri(GEMINI_MODEL_PATH + "{model}:generateContent?key={key}", resolvedModel, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(timeout));

            long latencyMs = System.currentTimeMillis() - start;
            String text = extractText(response);
            if (text == null || text.isBlank()) {
                log.warn("[AI_GEMINI_CALL_DEBUG] model={} httpStatus=200 success=false " +
                                "errorCode=AI_PROVIDER_EMPTY_RESPONSE latencyMs={}",
                        resolvedModel, latencyMs);
                return GeminiResult.fail("AI_PROVIDER_EMPTY_RESPONSE",
                        "Gemini responded but returned no text. The model may have filtered the content.",
                        latencyMs, 200, null);
            }
            log.info("[AI_GEMINI_CALL_DEBUG] model={} httpStatus=200 success=true latencyMs={}",
                    resolvedModel, latencyMs);
            return GeminiResult.ok(text, latencyMs);

        } catch (WebClientResponseException ex) {
            long latencyMs = System.currentTimeMillis() - start;
            int httpStatus = ex.getStatusCode().value();
            String providerMsg = safeProviderMessage(ex);
            log.warn("[AI_GEMINI_CALL_DEBUG] model={} httpStatus={} success=false latencyMs={} " +
                            "exceptionType=WebClientResponseException safeProviderMessage={}",
                    resolvedModel, httpStatus, latencyMs, providerMsg);

            return switch (httpStatus) {
                case 400 -> GeminiResult.fail("AI_BAD_REQUEST",
                        "Gemini rejected the request (HTTP 400). " +
                        (providerMsg != null ? providerMsg : "Check the model name and request format."),
                        latencyMs, httpStatus, providerMsg);

                case 401 -> GeminiResult.fail("AI_API_KEY_INVALID",
                        "Gemini API key is invalid (HTTP 401). " +
                        "Re-check the key in Google AI Studio (aistudio.google.com).",
                        latencyMs, httpStatus, providerMsg);

                case 403 -> GeminiResult.fail("AI_PROVIDER_AUTH_FORBIDDEN",
                        "Gemini access forbidden (HTTP 403). The API key may lack Generative Language API permissions, " +
                        "or the project may have API access restrictions. " +
                        (providerMsg != null ? providerMsg : ""),
                        latencyMs, httpStatus, providerMsg);

                case 404 -> GeminiResult.fail("AI_MODEL_NOT_FOUND",
                        "Gemini model '" + resolvedModel + "' was not found (HTTP 404). " +
                        "Try gemini-1.5-flash or gemini-2.0-flash-lite in AI settings.",
                        latencyMs, httpStatus, providerMsg);

                case 429 -> GeminiResult.fail("AI_QUOTA_EXCEEDED",
                        "Gemini rate limit or quota exceeded (HTTP 429). " +
                        "Try again later or upgrade your Google AI quota in the Google Cloud Console.",
                        latencyMs, httpStatus, providerMsg);

                case 503 -> GeminiResult.fail("AI_SERVICE_UNAVAILABLE",
                        "Gemini service is temporarily unavailable (HTTP 503). Try again later.",
                        latencyMs, httpStatus, providerMsg);

                default -> GeminiResult.fail("AI_PROVIDER_HTTP_ERROR",
                        "Gemini returned HTTP " + httpStatus + ". " +
                        (providerMsg != null ? providerMsg : "Check AI settings and server logs."),
                        latencyMs, httpStatus, providerMsg);
            };

        } catch (WebClientRequestException ex) {
            // Network-level failures before any HTTP response (DNS, TCP, TLS)
            long latencyMs = System.currentTimeMillis() - start;
            Throwable cause = rootCause(ex);
            String errorCode = classifyNetworkException(cause);
            String errorMsg  = networkErrorMessage(errorCode, resolvedModel, cause);
            log.warn("[AI_GEMINI_CALL_DEBUG] model={} success=false errorCode={} " +
                            "exceptionType=WebClientRequestException causeType={} causeMessage={} latencyMs={}",
                    resolvedModel, errorCode, cause.getClass().getName(),
                    cause.getMessage() != null ? cause.getMessage().split("\n")[0] : "null", latencyMs);
            return GeminiResult.fail(errorCode, errorMsg, latencyMs);

        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - start;

            if (isTimeoutCause(ex)) {
                log.warn("[AI_GEMINI_CALL_DEBUG] model={} success=false errorCode=AI_PROVIDER_TIMEOUT " +
                                "exceptionType={} latencyMs={}",
                        resolvedModel, ex.getClass().getSimpleName(), latencyMs);
                return GeminiResult.fail("AI_PROVIDER_TIMEOUT",
                        "Gemini did not respond within " + timeout + " seconds. " +
                        "Try increasing the timeout in AI settings.", latencyMs);
            }

            Throwable root = rootCause(ex);
            String errorCode = classifyNetworkException(root);
            String errorMsg  = networkErrorMessage(errorCode, resolvedModel, root);
            log.warn("[AI_GEMINI_CALL_DEBUG] model={} success=false errorCode={} " +
                            "exceptionType={} rootCauseType={} rootCauseMessage={} latencyMs={}",
                    resolvedModel, errorCode,
                    ex.getClass().getName(), root.getClass().getName(),
                    root.getMessage() != null ? root.getMessage().split("\n")[0] : "null", latencyMs);
            return GeminiResult.fail(errorCode, errorMsg, latencyMs);
        }
    }

    /** Extracts a short provider-supplied error message from a Gemini 4xx/5xx body. */
    private static String safeProviderMessage(WebClientResponseException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            // Gemini error body: {"error":{"code":403,"message":"API key not valid...","status":"PERMISSION_DENIED"}}
            int idx = body.indexOf("\"message\":");
            if (idx < 0) return null;
            int start = body.indexOf('"', idx + 10) + 1;
            int end   = body.indexOf('"', start);
            if (start > 0 && end > start) {
                String msg = body.substring(start, end);
                return msg.length() <= 300 ? msg : msg.substring(0, 300);
            }
        } catch (Exception ignore) { /* never let logging crash the call */ }
        return null;
    }

    /** Maps an exception to a specific AI error code. */
    private static String classifyNetworkException(Throwable ex) {
        if (ex == null) return "AI_SERVICE_UNAVAILABLE";
        String name = ex.getClass().getName().toLowerCase();
        String msg  = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        // Missing host â€” happens when the URI was built without a host (e.g. bare model name
        // treated as URI scheme). Distinct from a DNS / network failure.
        if (msg.contains("host is not specified") || msg.contains("no host") || msg.contains("host name may not be null")) {
            return "AI_PROVIDER_URL_MISSING";
        }

        // DNS failure
        if (ex instanceof java.net.UnknownHostException) return "AI_NETWORK_ERROR";
        if (name.contains("unknownhost")) return "AI_NETWORK_ERROR";

        // TCP connection refused / reset
        if (ex instanceof java.net.ConnectException) return "AI_PROVIDER_UNREACHABLE";
        if (name.contains("connectexception") || name.contains("annotatedconnect")) return "AI_PROVIDER_UNREACHABLE";

        // TLS/SSL errors
        if (ex instanceof javax.net.ssl.SSLException) return "AI_NETWORK_ERROR";
        if (name.contains("ssl") || name.contains("tls") || name.contains("handshake")) return "AI_NETWORK_ERROR";

        // Timeout at socket level
        if (ex instanceof java.net.SocketTimeoutException) return "AI_PROVIDER_TIMEOUT";
        if (name.contains("sockettimeout")) return "AI_PROVIDER_TIMEOUT";

        // Reactor/Netty specific connection issues
        if (name.contains("connection") && (msg.contains("refused") || msg.contains("reset") || msg.contains("closed"))) {
            return "AI_PROVIDER_UNREACHABLE";
        }

        return "AI_SERVICE_UNAVAILABLE";
    }

    private static String networkErrorMessage(String code, String model, Throwable root) {
        return switch (code) {
            case "AI_PROVIDER_URL_MISSING" ->
                    "Gemini API base URL is missing or malformed in the backend configuration. " +
                    "The request was sent without a valid host. This is a backend bug â€” not a network issue.";
            case "AI_NETWORK_ERROR" ->
                    "Cannot reach Gemini: " + root.getClass().getSimpleName() +
                    " â€” check that the server has outbound internet access to generativelanguage.googleapis.com";
            case "AI_PROVIDER_UNREACHABLE" ->
                    "Connection to Gemini was refused or reset. " +
                    "Check firewall rules and outbound internet access on the server. (" + root.getClass().getSimpleName() + ")";
            case "AI_PROVIDER_TIMEOUT" ->
                    "Gemini did not respond in time. Try increasing the timeout in AI settings.";
            default ->
                    "Gemini connection failed (" + root.getClass().getSimpleName() + "): " +
                    (root.getMessage() != null ? root.getMessage().split("\n")[0] : "unknown error") +
                    ". Check server network access to generativelanguage.googleapis.com";
        };
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cause = ex;
        int depth = 0;
        while (cause.getCause() != null && depth++ < 8) cause = cause.getCause();
        return cause;
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    private static boolean isTimeoutCause(Throwable ex) {
        if (ex == null) return false;
        if (ex instanceof TimeoutException) return true;
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("Timeout") || msg.contains("timeout"))) return true;
        return isTimeoutCause(ex.getCause());
    }

    public void assertConfiguredAndEnabled() {
        AiSettings settings = loadSettings();
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            throw AiServiceException.disabled();
        }
        KeyResult key = resolveKey(settings);
        if (key.isDecryptFailed()) throw AiServiceException.keyDecryptionFailed();
        if (!key.isUsable()) throw AiServiceException.keyNotConfigured();
    }
}

