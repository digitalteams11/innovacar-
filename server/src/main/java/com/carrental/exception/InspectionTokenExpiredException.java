package com.carrental.exception;

public class InspectionTokenExpiredException extends RuntimeException {
    public InspectionTokenExpiredException(String message) {
        super(message);
    }
}
