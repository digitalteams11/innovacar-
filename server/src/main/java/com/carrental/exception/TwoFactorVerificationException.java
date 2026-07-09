package com.carrental.exception;

/** Thrown when a 2FA code or challenge token fails verification. */
public class TwoFactorVerificationException extends RuntimeException {

    private final String errorCode;

    public TwoFactorVerificationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
