package com.carrental.controller;

import com.carrental.entity.SubscriptionPlan;
import com.carrental.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unauthenticated, read-only subscription plan listing for the public
 * marketing/pricing page. Deliberately exposes only the fields relevant to a
 * visitor comparing plans — never checkout URLs/IDs (Whop identifiers) or
 * internal limits like storage/API access, which stay behind the
 * authenticated {@code /api/subscriptions/plans} endpoint.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicPlansController {

    private final SubscriptionPlanRepository planRepository;

    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> plans() {
        List<Map<String, Object>> result = planRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toPublicView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toPublicView(SubscriptionPlan plan) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", plan.getCode());
        value.put("name", plan.getName());
        value.put("description", plan.getDescription());
        value.put("monthlyPrice", plan.getMonthlyPrice());
        value.put("yearlyPrice", plan.getYearlyPrice());
        value.put("currency", plan.getCurrency());
        value.put("maxVehicles", plan.getMaxVehicles());
        value.put("maxEmployees", plan.getMaxEmployees());
        value.put("maxGpsDevices", plan.getMaxGpsDevices());
        value.put("trialDays", plan.getTrialDays());
        value.put("billingCycleAllowedMonthly", plan.getBillingCycleAllowedMonthly());
        value.put("billingCycleAllowedYearly", plan.getBillingCycleAllowedYearly());
        value.put("highlighted", plan.getHighlighted());
        value.put("featuresJson", plan.getFeaturesJson());
        return value;
    }
}
