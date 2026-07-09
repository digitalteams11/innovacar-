package com.carrental.exception;

import lombok.Getter;

@Getter
public class TemplatePlanRequiredException extends RuntimeException {
    private final String requiredPlan;

    public TemplatePlanRequiredException(String requiredPlan) {
        super("This template is available in " + requiredPlan + " plan.");
        this.requiredPlan = requiredPlan;
    }
}
