package com.carrental.exception;

/**
 * A public contract-signing link ({@code contracts.qr_token}) rejected for a
 * reason specific to that link — expired, already used — as opposed to
 * {@link ResourceNotFoundException} (token doesn't exist / never issued) or
 * a generic {@link IllegalStateException} (contract not yet ready to sign).
 * Carries a stable errorCode so the public signing page
 * (frontend-web/src/pages/PublicContract.tsx) can show the exact, translated
 * message for each case instead of a generic "session expired" or 500-style
 * fallback.
 */
public class SignatureLinkException extends RuntimeException {
    private final String errorCode;

    public SignatureLinkException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static SignatureLinkException expired() {
        return new SignatureLinkException(
                "This signature link has expired. Please request a new link.", "SIGNATURE_LINK_EXPIRED");
    }

    public static SignatureLinkException alreadySigned() {
        return new SignatureLinkException(
                "This contract has already been signed.", "CONTRACT_ALREADY_SIGNED");
    }
}
