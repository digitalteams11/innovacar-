package com.carrental.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown for any client-self-fill-information error (invalid/expired/revoked
 * token, already submitted/approved, etc.) — see spec section 30 for the
 * full error-code taxonomy this maps to (a subset for the MVP slice).
 */
@Getter
public class ClientInfoRequestException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ClientInfoRequestException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
