package com.carrental.legal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class LegalDocumentNotFoundException extends RuntimeException {
    public LegalDocumentNotFoundException(String message) {
        super(message);
    }
}
