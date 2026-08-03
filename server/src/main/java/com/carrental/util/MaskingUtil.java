package com.carrental.util;

/**
 * Shared PII-masking helpers used by both the admin-facing DTO mapping and
 * the public signing view/PDF, so a driver's license/token is never
 * rendered in full outside an explicit "view full" action.
 */
public final class MaskingUtil {

    private MaskingUtil() {}

    /** Keeps the first 2 and last 2 characters, masks the middle with '*'. */
    public static String maskLicense(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }
        String head = trimmed.substring(0, 2);
        String tail = trimmed.substring(trimmed.length() - 2);
        return head + "*".repeat(trimmed.length() - 4) + tail;
    }

    /** First 6 chars only — never log/return a full token. */
    public static String maskToken(String token) {
        if (token == null || token.length() < 6) return "***";
        return token.substring(0, 6) + "...";
    }
}
