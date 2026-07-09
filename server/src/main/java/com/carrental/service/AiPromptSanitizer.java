package com.carrental.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Strips anything secret-shaped out of text before it is ever sent to
 * Gemini. Applied to every piece of user-supplied or backend-aggregated
 * text that becomes part of an AI prompt — defense in depth on top of the
 * callers only ever passing curated, non-sensitive fields in the first
 * place.
 */
@Service
public class AiPromptSanitizer {

    private static final int MAX_PROMPT_CHARS = 12_000;

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]{10,}");
    private static final Pattern API_KEY = Pattern.compile("(?i)\\b(AIza|sk-|ghp_|gho_|xox[baprs]-)[A-Za-z0-9_-]{10,}\\b");
    private static final Pattern GENERIC_SECRET_FIELD = Pattern.compile(
            "(?i)\"?(password|apiKey|api_key|secret|token|smtpPassword|smtp_password|privateKey|private_key)\"?\\s*[:=]\\s*\"?[^\",\\s]{3,}\"?");
    private static final Pattern CREDIT_CARD = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    /** Removes anything secret-shaped and caps total length before it reaches a prompt. */
    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = text;
        result = JWT.matcher(result).replaceAll("[REDACTED_TOKEN]");
        result = BEARER.matcher(result).replaceAll("[REDACTED_TOKEN]");
        result = API_KEY.matcher(result).replaceAll("[REDACTED_KEY]");
        result = GENERIC_SECRET_FIELD.matcher(result).replaceAll("$1: [REDACTED]");
        result = CREDIT_CARD.matcher(result).replaceAll("[REDACTED_CARD]");
        if (result.length() > MAX_PROMPT_CHARS) {
            result = result.substring(0, MAX_PROMPT_CHARS) + "\n[...truncated]";
        }
        return result;
    }

    /** Masks all but the last 2 characters — used for display, never sent to Gemini at all. */
    public String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int visible = Math.min(2, value.length());
        return "*".repeat(Math.max(8, value.length() - visible)) + value.substring(value.length() - visible);
    }
}
