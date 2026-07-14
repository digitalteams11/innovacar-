package com.carrental.legal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PrivacyRequestNotFoundException extends RuntimeException {
    public PrivacyRequestNotFoundException(String message) {
        super(message);
    }
}
