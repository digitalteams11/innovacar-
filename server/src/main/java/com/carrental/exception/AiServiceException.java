package com.carrental.exception;

import lombok.Getter;

/**
 * Thrown for any AI/Gemini failure that must reach the client as a clean,
 * normalized error — never the raw Gemini exception/response, which could
 * contain implementation details or (in error payloads) echoed prompt data.
 *
 * <p>Error codes used across the platform:
 * <ul>
 *   <li>AI_DISABLED          — platform-level toggle is off (Super Admin → AI & Automation → Enable AI)</li>
 *   <li>AI_KEY_NOT_CONFIGURED — no Gemini API key saved yet</li>
 *   <li>AI_CHAT_DISABLED     — chat feature toggle is off while platform AI is on</li>
 *   <li>AI_INVALID_API_KEY   — Gemini rejected the key (401/403)</li>
 *   <li>AI_PROVIDER_TIMEOUT  — Gemini did not respond within the configured timeout</li>
 *   <li>AI_LIMIT_REACHED     — per-user or per-agency daily request limit exceeded</li>
 *   <li>AI_FEATURE_NOT_AVAILABLE — feature not included in current subscription plan</li>
 *   <li>AI_SERVICE_UNAVAILABLE   — unexpected Gemini / network error</li>
 * </ul>
 */
@Getter
public class AiServiceException extends RuntimeException {
    private final String errorCode;

    public AiServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    // ── Factory methods — one per distinct failure scenario ───────────────────

    /** Platform-level AI toggle is off. Super Admin must enable it in AI & Automation settings. */
    public static AiServiceException disabled() {
        return new AiServiceException(
                "AI is disabled by the platform administrator. Enable it in Super Admin → AI & Automation.",
                "AI_DISABLED");
    }

    /** API key has never been saved, or was cleared. */
    public static AiServiceException keyNotConfigured() {
        return new AiServiceException(
                "AI API key is not configured. Super Admin must save a Gemini API key in AI & Automation settings.",
                "AI_KEY_NOT_CONFIGURED");
    }

    /** Platform AI is on, key is present, but the Chat feature toggle is off. */
    public static AiServiceException chatDisabled() {
        return new AiServiceException(
                "AI Chat Assistant feature is disabled. Super Admin can enable it in AI & Automation settings.",
                "AI_CHAT_DISABLED");
    }

    /**
     * The encrypted key in DB cannot be decrypted — secret may have changed,
     * or the stored value was written by a different encryption implementation.
     * User must re-enter the Gemini API key and save again.
     */
    public static AiServiceException keyDecryptionFailed() {
        return new AiServiceException(
                "Saved Gemini API key cannot be decrypted. Re-enter and save the API key in AI & Automation settings.",
                "AI_KEY_DECRYPTION_FAILED");
    }

    /** Gemini rejected the API key (HTTP 401 or 403). */
    public static AiServiceException invalidApiKey() {
        return new AiServiceException(
                "Gemini rejected the API key. Check the key is correct and has Generative Language API access.",
                "AI_INVALID_API_KEY");
    }

    /** Gemini did not respond before the configured timeout. */
    public static AiServiceException providerTimeout() {
        return new AiServiceException(
                "Gemini did not respond before the timeout. Try increasing timeout seconds in AI settings.",
                "AI_PROVIDER_TIMEOUT");
    }

    public static AiServiceException unavailable() {
        return new AiServiceException(
                "AI service is unavailable. Please try again later.",
                "AI_SERVICE_UNAVAILABLE");
    }

    public static AiServiceException rateLimited() {
        return new AiServiceException(
                "AI usage limit reached for your account. Try again tomorrow.",
                "AI_LIMIT_REACHED");
    }

    public static AiServiceException featureDisabled(String feature) {
        return new AiServiceException(
                "This AI feature (" + feature + ") is not available on your current plan.",
                "AI_FEATURE_NOT_AVAILABLE");
    }

    // ── New provider-independent-architecture error codes ─────────────────────

    public static AiServiceException noActiveProvider() {
        return new AiServiceException(
                "No AI provider is active. Configure and activate a provider in Super Admin → AI & Automation → Providers.",
                "AI_NO_ACTIVE_PROVIDER");
    }

    public static AiServiceException providerDisabled() {
        return new AiServiceException(
                "The active AI provider is disabled. Enable it or activate another provider.",
                "AI_PROVIDER_DISABLED");
    }

    public static AiServiceException modelDisabled() {
        return new AiServiceException(
                "The selected AI model is disabled. Choose another model in AI & Automation → Models.",
                "AI_MODEL_DISABLED");
    }

    public static AiServiceException automationNotFound() {
        return new AiServiceException(
                "Unknown AI automation code.",
                "AI_AUTOMATION_NOT_FOUND");
    }

    public static AiServiceException automationDisabled() {
        return new AiServiceException(
                "This AI automation is currently disabled.",
                "AI_AUTOMATION_DISABLED");
    }

    /** The automation exists in the catalog but no real backend flow triggers it yet. */
    public static AiServiceException automationNotWired() {
        return new AiServiceException(
                "This AI automation is configurable but not yet connected to a live feature.",
                "AI_AUTOMATION_NOT_WIRED");
    }

    public static AiServiceException crossAgencyDenied() {
        return new AiServiceException(
                "You are not authorized to access AI data for another agency.",
                "AI_CROSS_AGENCY_DENIED");
    }

    public static AiServiceException invalidCustomEndpoint(String reason) {
        return new AiServiceException(
                "Custom AI provider endpoint rejected: " + reason,
                "AI_INVALID_CUSTOM_ENDPOINT");
    }

    public static AiServiceException providerNotFound() {
        return new AiServiceException("AI provider not found.", "AI_PROVIDER_NOT_FOUND");
    }

    public static AiServiceException providerInUse() {
        return new AiServiceException(
                "This provider cannot be deleted while it is active or referenced by usage logs. Disable it instead.",
                "AI_PROVIDER_IN_USE");
    }

    // ── Deprecated aliases — kept to avoid breaking callers in tests ──────────

    /** @deprecated use {@link #keyNotConfigured()} */
    @Deprecated
    public static AiServiceException notConfigured() {
        return keyNotConfigured();
    }
}
