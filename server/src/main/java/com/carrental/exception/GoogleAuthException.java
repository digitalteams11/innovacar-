package com.carrental.exception;

/** Structured Google OAuth failure — carries a stable errorCode the frontend maps to a translated message. */
public class GoogleAuthException extends RuntimeException {
    private final String errorCode;

    public GoogleAuthException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
