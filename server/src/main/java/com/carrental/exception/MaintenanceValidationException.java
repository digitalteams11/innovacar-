package com.carrental.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class MaintenanceValidationException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> details;

    public MaintenanceValidationException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public MaintenanceValidationException(String message, String errorCode, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
