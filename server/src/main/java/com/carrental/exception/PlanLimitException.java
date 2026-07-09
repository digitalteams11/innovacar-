package com.carrental.exception;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thrown when a tenant has reached their subscription plan limit for a resource.
 * Results in HTTP 403 with errorCode PLAN_LIMIT_REACHED.
 */
@Getter
public class PlanLimitException extends RuntimeException {

    private final String       resource;
    private final long         used;
    private final long         limit;
    private final List<String> upgradePlans;
    private final String       currentPlan;

    public PlanLimitException(String resource, long used, long limit,
                              List<String> upgradePlans, String currentPlan) {
        super("PLAN_LIMIT_REACHED:" + resource);
        this.resource     = resource;
        this.used         = used;
        this.limit        = limit;
        this.upgradePlans = upgradePlans;
        this.currentPlan  = currentPlan;
    }

    /** @deprecated use the 5-arg constructor */
    public PlanLimitException(String resource, long used, long limit, List<String> upgradePlans) {
        this(resource, used, limit, upgradePlans, null);
    }

    public Map<String, Object> toDetail() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource",     resource);
        data.put("current",      used);
        data.put("limit",        limit);
        data.put("currentPlan",  currentPlan);
        data.put("upgradePlans", upgradePlans);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("errorCode", "PLAN_LIMIT_REACHED");
        response.put("message",   "Your current plan limit has been reached.");
        response.put("data",      data);
        return response;
    }
}
