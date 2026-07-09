package com.carrental.exception;

public class DuplicateClientException extends ClientDuplicateException {
    public DuplicateClientException(String message, String field) {
        super(message, "CLIENT_DUPLICATE_" + field.toUpperCase(), field);
    }

    public DuplicateClientException(String message, String field, Long existingClientId) {
        super(message, "CLIENT_DUPLICATE_" + field.toUpperCase(), field, existingClientId);
    }
}
