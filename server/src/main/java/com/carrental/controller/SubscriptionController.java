package com.carrental.controller;

import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.Tenant;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Subscription REST controller for both agency admins and super admins.
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final VehicleRepository vehicleRepository;
    private final EmployeeRepository employeeRepository;
    private final ReservationRepository reservationRepository;

    // ── GET /api/subscriptions/status ────────────────────────────────────────
    /** Returns current tenant's subscription status, plan, usage, and trial info. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        long vehicleCount = vehicleRepository.countByTenantId(tenantId);
        long employeeCount = employeeRepository.findAllByTenantId(tenantId).size();
        long reservationCount = reservationRepository.findAllByTenantId(tenantId).size();
        long gpsCount = vehicleRepository.findAllByTenantId(tenantId).stream()
                .filter(v -> v.getGpsDeviceId() != null).count();

        long daysRemaining = 0;
        boolean inTrial = false;
        if (tenant.getTrialEndDate() != null) {
            daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getTrialEndDate());
            inTrial = daysRemaining > 0;
        }
        if (tenant.getSubscriptionEndDate() != null && !inTrial) {
            daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getSubscriptionEndDate());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planName", tenant.getPlanName());
        result.put("status", tenant.getStatus());
        result.put("subscriptionActive", tenant.isSubscriptionValid());
        result.put("subscriptionEndDate", tenant.getSubscriptionEndDate());
        result.put("trialEndDate", tenant.getTrialEndDate());
        result.put("inTrial", inTrial);
        result.put("daysRemaining", Math.max(0, daysRemaining));
        SubscriptionPlan plan = planRepository.findByName(tenant.getPlanName()).orElse(null);
        if (plan != null) {
            result.put("maxVehicles", plan.getMaxVehicles());
            result.put("maxEmployees", plan.getMaxEmployees());
            result.put("maxGpsDevices", plan.getMaxGpsDevices());
            result.put("maxReservations", plan.getMaxReservations());
            result.put("storageLimitMb", plan.getStorageLimitMb());
            if (plan.getFeaturesJson() != null) {
                result.put("featuresJson", plan.getFeaturesJson());
            }
        } else {
            result.put("maxVehicles", tenant.getMaxVehicles());
            result.put("maxEmployees", tenant.getMaxEmployees());
            result.put("maxGpsDevices", tenant.getMaxGpsDevices());
            result.put("maxReservations", tenant.getMaxReservations());
            result.put("storageLimitMb", tenant.getStorageLimitMb());
        }
        result.put("vehicleCount", vehicleCount);
        result.put("employeeCount", employeeCount);
        result.put("reservationCount", reservationCount);
        result.put("gpsCount", gpsCount);
        return ResponseEntity.ok(result);
    }

    // ── GET /api/subscriptions/plans ─────────────────────────────────────────
    /** Returns all available subscription plans for the agency to browse. */
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAvailablePlans() {
        return ResponseEntity.ok(planRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc());
    }

    // ── POST /api/subscriptions/upgrade ──────────────────────────────────────
    /** Agency admin upgrades to a different plan. */
    @PostMapping("/upgrade")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> upgradePlan(@RequestBody Map<String, String> body) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        String planCode = body.get("planCode");
        String billingCycle = body.getOrDefault("billingCycle", "monthly");

        SubscriptionPlan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        tenant.setPlanName(plan.getName());
        tenant.setSubscriptionActive(true);
        tenant.setStatus("ACTIVE");

        int months = "yearly".equals(billingCycle) ? 12 : 1;
        LocalDate endDate = tenant.getSubscriptionEndDate();
        if (endDate == null || endDate.isBefore(LocalDate.now())) {
            endDate = LocalDate.now();
        }
        tenant.setSubscriptionEndDate(endDate.plusMonths(months));

        tenant.setMaxVehicles(plan.getMaxVehicles());
        tenant.setMaxEmployees(plan.getMaxEmployees());
        tenant.setMaxGpsDevices(plan.getMaxGpsDevices());
        tenant.setMaxReservations(plan.getMaxReservations());
        tenant.setStorageLimitMb(plan.getStorageLimitMb());

        tenantRepository.save(tenant);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Upgraded to " + plan.getName(),
                "planName", plan.getName(),
                "billingCycle", billingCycle,
                "subscriptionEndDate", tenant.getSubscriptionEndDate().toString()
        ));
    }

    // ── POST /api/subscriptions/activate ─────────────────────────────────────
    @PostMapping("/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> activateSubscription() {
        subscriptionService.activateSubscription();
        return ResponseEntity.ok(Map.of("message", "Subscription activated successfully"));
    }

    // ── POST /api/subscriptions/extend ───────────────────────────────────────
    @PostMapping("/extend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> extendSubscription(@RequestParam int days) {
        subscriptionService.extendSubscription(days);
        return ResponseEntity.ok(Map.of("message", "Subscription extended successfully by " + days + " days"));
    }

    // ── POST /api/subscriptions/addon ────────────────────────────────────────
    /** Purchase an add-on (extra vehicles, employees, GPS, storage). */
    @PostMapping("/addon")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> purchaseAddon(@RequestBody Map<String, Integer> body) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        if (body.containsKey("extraVehicles")) {
            tenant.setMaxVehicles((tenant.getMaxVehicles() != null ? tenant.getMaxVehicles() : 0) + body.get("extraVehicles"));
        }
        if (body.containsKey("extraEmployees")) {
            tenant.setMaxEmployees((tenant.getMaxEmployees() != null ? tenant.getMaxEmployees() : 0) + body.get("extraEmployees"));
        }
        if (body.containsKey("extraGps")) {
            tenant.setMaxGpsDevices((tenant.getMaxGpsDevices() != null ? tenant.getMaxGpsDevices() : 0) + body.get("extraGps"));
        }
        if (body.containsKey("extraStorageMb")) {
            tenant.setStorageLimitMb((tenant.getStorageLimitMb() != null ? tenant.getStorageLimitMb() : 0) + body.get("extraStorageMb"));
        }

        tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of("success", true, "message", "Add-on purchased successfully"));
    }
}
