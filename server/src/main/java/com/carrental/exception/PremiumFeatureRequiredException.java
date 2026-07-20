package com.carrental.exception;

import lombok.Getter;

/** Thrown when a tenant's resolved plan does not grant a Premium-gated feature (e.g. AUTOMATION_CENTER). */
@Getter
public class PremiumFeatureRequiredException extends RuntimeException {
    private final String feature;

    public PremiumFeatureRequiredException(String feature, String message) {
        super(message);
        this.feature = feature;
    }
}
