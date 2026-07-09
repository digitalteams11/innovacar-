package com.carrental.exception;

/**
 * Thrown whenever a promo code fails backend validation (expired, inactive,
 * usage limit reached, wrong plan/billing cycle, etc). The errorCode is one
 * of the PROMO_* codes consumed by the frontend to show an exact message.
 */
public class PromoCodeException extends RuntimeException {
    private final String errorCode;

    public PromoCodeException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
