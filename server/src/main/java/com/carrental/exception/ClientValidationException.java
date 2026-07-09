package com.carrental.exception;

public class ClientValidationException extends RuntimeException {
    private final String errorCode;

    public ClientValidationException(String message) {
        this(message, "VALIDATION_ERROR");
    }

    public ClientValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
