package com.carrental.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Raised when a Super Admin data-reset request fails one of the mandatory
 * safety gates (permission, email verification, 2FA, password, confirmation
 * code, or production lockout). Carries the exact HTTP status and errorCode
 * the frontend needs to render the right blocking message.
 */
@Getter
public class DataResetSecurityException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> data;

    public DataResetSecurityException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.data = null;
    }

    public DataResetSecurityException(HttpStatus status, String errorCode, String message, Map<String, Object> data) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.data = data;
    }
}
