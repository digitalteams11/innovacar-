package com.carrental.service.provider;

import java.util.concurrent.TimeoutException;

/**
 * Shared network-exception classification, extracted from the original
 * {@code GeminiClientService} so the same hardened logic (real production
 * debugging behind these code paths) is not duplicated across provider
 * clients.
 */
public final class AiProviderHttpSupport {

    private AiProviderHttpSupport() {
    }

    public static Throwable rootCause(Throwable ex) {
        Throwable cause = ex;
        int depth = 0;
        while (cause.getCause() != null && depth++ < 8) cause = cause.getCause();
        return cause;
    }

    public static boolean isTimeoutCause(Throwable ex) {
        if (ex == null) return false;
        if (ex instanceof TimeoutException) return true;
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("Timeout") || msg.contains("timeout"))) return true;
        return isTimeoutCause(ex.getCause());
    }

    public static String classifyNetworkException(Throwable ex) {
        if (ex == null) return "AI_SERVICE_UNAVAILABLE";
        String name = ex.getClass().getName().toLowerCase();
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        if (msg.contains("host is not specified") || msg.contains("no host") || msg.contains("host name may not be null")) {
            return "AI_PROVIDER_URL_MISSING";
        }
        if (ex instanceof java.net.UnknownHostException) return "AI_NETWORK_ERROR";
        if (name.contains("unknownhost")) return "AI_NETWORK_ERROR";

        if (ex instanceof java.net.ConnectException) return "AI_PROVIDER_UNREACHABLE";
        if (name.contains("connectexception") || name.contains("annotatedconnect")) return "AI_PROVIDER_UNREACHABLE";

        if (ex instanceof javax.net.ssl.SSLException) return "AI_NETWORK_ERROR";
        if (name.contains("ssl") || name.contains("tls") || name.contains("handshake")) return "AI_NETWORK_ERROR";

        if (ex instanceof java.net.SocketTimeoutException) return "AI_PROVIDER_TIMEOUT";
        if (name.contains("sockettimeout")) return "AI_PROVIDER_TIMEOUT";

        if (name.contains("connection") && (msg.contains("refused") || msg.contains("reset") || msg.contains("closed"))) {
            return "AI_PROVIDER_UNREACHABLE";
        }
        return "AI_SERVICE_UNAVAILABLE";
    }

    /**
     * Providers that reject an oversized {@code max_tokens}/{@code max_completion_tokens}
     * value on a 400 respond with the model's actual cap embedded in the error
     * text (e.g. Groq: "must be less than or equal to `512`"; OpenAI-style:
     * "supports at most 4096 completion tokens"). Parsing it lets callers
     * retry once with a clamped value instead of hardcoding per-model limits
     * that providers change over time.
     */
    private static final java.util.regex.Pattern[] MAX_TOKENS_CAP_PATTERNS = {
            java.util.regex.Pattern.compile("less than or equal to `?(\\d+)`?", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("supports at most `?(\\d+)`?", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("maximum value for `?max_tokens`? is `?(\\d+)`?", java.util.regex.Pattern.CASE_INSENSITIVE),
    };

    public static Integer extractMaxTokensCap(String providerMessage) {
        if (providerMessage == null || providerMessage.isBlank()) return null;
        for (java.util.regex.Pattern pattern : MAX_TOKENS_CAP_PATTERNS) {
            var matcher = pattern.matcher(providerMessage);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignore) {
                    // try next pattern
                }
            }
        }
        return null;
    }

    public static String networkErrorMessage(String code, String providerName, Throwable root) {
        return switch (code) {
            case "AI_PROVIDER_URL_MISSING" ->
                    providerName + " API base URL is missing or malformed in the backend configuration. This is a backend bug, not a network issue.";
            case "AI_NETWORK_ERROR" ->
                    "Cannot reach " + providerName + ": " + root.getClass().getSimpleName() +
                            " — check that the server has outbound internet access.";
            case "AI_PROVIDER_UNREACHABLE" ->
                    "Connection to " + providerName + " was refused or reset. Check firewall rules and outbound access. (" +
                            root.getClass().getSimpleName() + ")";
            case "AI_PROVIDER_TIMEOUT" ->
                    providerName + " did not respond in time. Try increasing the timeout in AI settings.";
            default ->
                    providerName + " connection failed (" + root.getClass().getSimpleName() + "): " +
                            (root.getMessage() != null ? root.getMessage().split("\n")[0] : "unknown error");
        };
    }
}
