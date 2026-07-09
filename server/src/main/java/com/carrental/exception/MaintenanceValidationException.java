package com.carrental.exception;

import lombok.Getter;

@Getter
public class MaintenanceValidationException extends RuntimeException {
    private final String errorCode;

    public MaintenanceValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
