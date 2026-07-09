package com.carrental.exception;

import com.carrental.entity.TwoFactorMethod;
import lombok.Getter;

@Getter
public class TwoFactorRequiredException extends RuntimeException {
    private final TwoFactorMethod method;

    public TwoFactorRequiredException(TwoFactorMethod method) {
        super("Two-factor authentication code is required.");
        this.method = method;
    }
}
