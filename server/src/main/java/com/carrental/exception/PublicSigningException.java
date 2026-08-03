package com.carrental.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown for any additional-driver public-signing error (invalid/expired/
 * revoked token, already signed, contract cancelled, validation failure,
 * etc.) — mirrors {@link ClientInfoRequestException}'s code+status shape so
 * the frontend gets a precise, non-leaking, machine-readable error per
 * state instead of a raw exception message.
 */
@Getter
public class PublicSigningException extends RuntimeException {

    public enum Reason { INVALID, EXPIRED, REVOKED, ALREADY_SIGNED, CANCELLED, NOT_REQUIRED, VALIDATION }

    private final Reason reason;
    private final HttpStatus status;

    public PublicSigningException(Reason reason, HttpStatus status, String message) {
        super(message);
        this.reason = reason;
        this.status = status;
    }
}
