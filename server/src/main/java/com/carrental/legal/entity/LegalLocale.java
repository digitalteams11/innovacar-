package com.carrental.legal.entity;

/**
 * Supported languages for legal document content. Mirrors the app's three
 * supported UI languages (Arabic, French, English).
 */
public enum LegalLocale {
    EN, FR, AR;

    /** Parses a free-form language code (e.g. a User.language value like "en", "fr-FR") into a LegalLocale, defaulting to FR (Morocco's primary business language). */
    public static LegalLocale fromCode(String code) {
        if (code == null || code.isBlank()) return FR;
        String normalized = code.trim().substring(0, Math.min(2, code.trim().length())).toUpperCase();
        try {
            return LegalLocale.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return FR;
        }
    }

    /** Fallback order used when no published version exists in the requested locale. */
    public LegalLocale[] fallbackChain() {
        return switch (this) {
            case FR -> new LegalLocale[]{FR, EN, AR};
            case AR -> new LegalLocale[]{AR, FR, EN};
            case EN -> new LegalLocale[]{EN, FR, AR};
        };
    }
}
