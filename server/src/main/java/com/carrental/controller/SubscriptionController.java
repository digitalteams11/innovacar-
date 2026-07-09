package com.carrental.controller;

import com.carrental.entity.AuditLog;
import com.carrental.entity.CancellationReason;
import com.carrental.entity.CancellationRequest;
import com.carrental.entity.CancellationRequestStatus;
import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ContractRepository contractRepository;
    private final SubscriptionInvoiceRepository subscriptionInvoiceRepository;
    private final CancellationRequestRepository cancellationRequestRepository;
    private final AuditLogRepository auditLogRepository;

    // ── GET /api/subscriptions/status ────────────────────────────────────────
    /** Returns current tenant's subscription status, plan, usage, and trial info. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.ok(defaultSubscriptionStatus());
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.ok(defaultSubscriptionStatus());
        }
        ensureDefaultTrial(tenant);
        SubscriptionPlan plan = resolvePlan(tenant);
        tenant = subscriptionService.repairSubscriptionState(tenant, plan);

        long vehicleCount = vehicleRepository.countByTenantId(tenantId);
        long employeeCount = employeeRepository.findAllByTenantId(tenantId).size();
        long reservationCount = reservationRepository.findAllByTenantId(tenantId).size();
        long contractCount = contractRepository.countByTenantId(tenantId);
        long gpsCount = vehicleRepository.findAllByTenantId(tenantId).stream()
                .filter(v -> v.getGpsDeviceId() != null).count();

        String planCode = plan != null && plan.getCode() != null
                ? plan.getCode().toUpperCase(Locale.ROOT)
                : tenant.getPlanName().toUpperCase(Locale.ROOT);
        boolean inTrial = "TRIAL".equals(planCode)
                && "TRIAL".equalsIgnoreCase(tenant.getStatus())
                && tenant.getTrialEndDate() != null
                && !LocalDate.now().isAfter(tenant.getTrialEndDate());
        long remainingTrialDays = inTrial
                ? Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), tenant.getTrialEndDate()))
                : 0;
        long daysRemaining = inTrial ? remainingTrialDays : tenant.getSubscriptionEndDate() == null
                ? 0
                : Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), tenant.getSubscriptionEndDate()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planCode", planCode);
        result.put("planName", tenant.getPlanName());
        result.put("status", tenant.getStatus());
        result.put("isTrial", inTrial);
        result.put("trialEndsAt", inTrial ? tenant.getTrialEndDate() : null);
        result.put("remainingTrialDays", remainingTrialDays);
        result.put("currentPeriodEnd", tenant.getSubscriptionEndDate());
        result.put("subscriptionActive", tenant.isSubscriptionValid());
        result.put("subscriptionEndDate", tenant.getSubscriptionEndDate());
        result.put("hasFreeAccess", tenant.hasActiveFreeAccess());
        result.put("freeAccessUntil", tenant.getFreeAccessUntil());
        result.put("freeAccessReason", tenant.getFreeAccessReason());
        result.put("trialEndDate", inTrial ? tenant.getTrialEndDate() : null);
        result.put("inTrial", inTrial);
        result.put("daysRemaining", daysRemaining);
        result.put("remainingDays", daysRemaining);
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
        result.put("contractCount", contractCount);
        result.put("gpsCount", gpsCount);
        result.put("maxContracts", 100);
        result.put("cancelScheduled", tenant.isCancelScheduled());
        result.put("cancelEffectiveAt", tenant.getCancelEffectiveAt());
        result.put("cancellationReason", tenant.getCancellationReason());
        return ResponseEntity.ok(withEnvelope(result));
    }

    // ── GET /api/subscriptions/plans ─────────────────────────────────────────
    private Map<String, Object> defaultSubscriptionStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planCode", "TRIAL");
        result.put("planName", "TRIAL");
        result.put("status", "TRIAL");
        result.put("isTrial", true);
        result.put("trialEndsAt", LocalDate.now().plusDays(60));
        result.put("remainingTrialDays", 60);
        result.put("currentPeriodEnd", null);
        result.put("subscriptionActive", false);
        result.put("subscriptionEndDate", null);
        result.put("trialEndDate", LocalDate.now().plusDays(60));
        result.put("inTrial", true);
        result.put("daysRemaining", 60);
        result.put("remainingDays", 60);
        result.put("maxVehicles", 4);
        result.put("maxEmployees", 5);
        result.put("maxGpsDevices", 5);
        result.put("maxReservations", 100);
        result.put("maxContracts", 100);
        result.put("storageLimitMb", 1024);
        result.put("vehicleCount", 0);
        result.put("employeeCount", 0);
        result.put("reservationCount", 0);
        result.put("contractCount", 0);
        result.put("gpsCount", 0);
        return withEnvelope(result);
    }

    private void ensureDefaultTrial(Tenant tenant) {
        boolean changed = false;
        boolean trialPlan = tenant.getPlanName() == null
                || tenant.getPlanName().isBlank()
                || "TRIAL".equalsIgnoreCase(tenant.getPlanName());
        if (tenant.getPlanName() == null || tenant.getPlanName().isBlank()) {
            tenant.setPlanName("TRIAL");
            changed = true;
        }
        if (tenant.getStatus() == null || tenant.getStatus().isBlank()) {
            tenant.setStatus(trialPlan ? "TRIAL" : "ACTIVE");
            changed = true;
        }
        if (trialPlan && tenant.getTrialStartDate() == null) {
            tenant.setTrialStartDate(LocalDate.now());
            changed = true;
        }
        if (trialPlan && tenant.getTrialEndDate() == null) {
            tenant.setTrialEndDate(LocalDate.now().plusDays(60));
            changed = true;
        }
        if (tenant.getMaxVehicles() == null || tenant.getMaxVehicles() <= 0) {
            tenant.setMaxVehicles(4);
            changed = true;
        }
        if (tenant.getMaxEmployees() == null || tenant.getMaxEmployees() <= 0) {
            tenant.setMaxEmployees(5);
            changed = true;
        }
        if (tenant.getMaxGpsDevices() == null || tenant.getMaxGpsDevices() <= 0) {
            tenant.setMaxGpsDevices(5);
            changed = true;
        }
        if (tenant.getMaxReservations() == null || tenant.getMaxReservations() <= 0) {
            tenant.setMaxReservations(100);
            changed = true;
        }
        if (tenant.getStorageLimitMb() == null || tenant.getStorageLimitMb() <= 0) {
            tenant.setStorageLimitMb(1024);
            changed = true;
        }
        if (changed) {
            tenantRepository.save(tenant);
        }
    }

    private Map<String, Object> withEnvelope(Map<String, Object> status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", status.get("planName"));
        data.put("planCode", status.get("planCode"));
        data.put("planName", status.get("planName"));
        data.put("status", status.get("status"));
        data.put("isTrial", status.get("isTrial"));
        data.put("trialEndsAt", status.get("trialEndsAt"));
        data.put("remainingTrialDays", status.get("remainingTrialDays"));
        data.put("currentPeriodEnd", status.get("currentPeriodEnd"));
        data.put("remainingDays", status.get("daysRemaining"));
        data.put("cancelScheduled", status.get("cancelScheduled"));
        data.put("cancelEffectiveAt", status.get("cancelEffectiveAt"));
        data.put("cancellationReason", status.get("cancellationReason"));
        data.put("limits", Map.of(
                "vehicles", status.get("maxVehicles"),
                "employees", status.get("maxEmployees"),
                "gps", status.get("maxGpsDevices"),
                "reservations", status.get("maxReservations"),
                "contracts", status.getOrDefault("maxContracts", 100)
        ));
        data.put("usage", Map.of(
                "vehicles", status.get("vehicleCount"),
                "employees", status.get("employeeCount"),
                "gps", status.get("gpsCount"),
                "reservations", status.get("reservationCount"),
                "contracts", status.getOrDefault("contractCount", 0)
        ));
        Map<String, Object> response = new LinkedHashMap<>(status);
        response.put("success", true);
        response.put("message", "Subscription status loaded successfully");
        response.put("data", data);
        return response;
    }

    private SubscriptionPlan resolvePlan(Tenant tenant) {
        if (tenant.getPlanName() == null || tenant.getPlanName().isBlank()) return null;
        return planRepository.findByName(tenant.getPlanName())
                .or(() -> planRepository.findByCode(tenant.getPlanName()))
                .orElse(null);
    }

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

        int months = "yearly".equals(billingCycle) ? 12 : 1;
        tenant = subscriptionService.activatePaidPlan(tenant, plan, months);

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

    // ── GET /api/subscriptions/invoices ──────────────────────────────────────
    /** Returns the current tenant's own subscription billing history. */
    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> getInvoices() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Map<String, Object>> invoices = subscriptionInvoiceRepository
                .findAllByTenantIdOrderByIssuedAtDesc(tenantId)
                .stream()
                .map(invoice -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", invoice.getId());
                    map.put("invoiceNumber", invoice.getInvoiceNumber());
                    map.put("planName", invoice.getPlan().getName());
                    map.put("billingCycle", invoice.getBillingCycle());
                    map.put("subtotal", invoice.getSubtotal());
                    map.put("discount", invoice.getDiscount());
                    map.put("total", invoice.getTotal());
                    map.put("currency", invoice.getCurrency());
                    map.put("status", invoice.getStatus());
                    map.put("issuedAt", invoice.getIssuedAt());
                    map.put("paidAt", invoice.getPaidAt());
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Billing history loaded successfully",
                "data", invoices
        ));
    }

    // ── POST /api/subscriptions/cancel ───────────────────────────────────────
    /**
     * Agency admin schedules end-of-period cancellation. Does NOT cancel immediately.
     * Access continues until {@code cancelEffectiveAt} (= current period end).
     * Body: { "reason": "TOO_EXPENSIVE", "feedback": "...", "confirm": true }
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal User caller) {
        Long tenantId = TenantContext.getCurrentTenantId();

        if (body == null || !Boolean.TRUE.equals(body.get("confirm"))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please confirm the cancellation request.",
                    "errorCode", "CONFIRM_REQUIRED"));
        }

        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : "NOT_SPECIFIED";
        String feedback = body.get("feedback") != null ? String.valueOf(body.get("feedback")) : null;

        try {
            Tenant saved = subscriptionService.scheduleCancellation(tenantId, reason, feedback, caller);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cancellation scheduled. Your access continues until " + saved.getCancelEffectiveAt().toLocalDate() + ".",
                    "cancelEffectiveAt", saved.getCancelEffectiveAt().toString(),
                    "status", saved.getStatus()
            ));
        } catch (IllegalStateException e) {
            String errorCode = e.getMessage().contains("already cancelled") ? "ALREADY_CANCELLED"
                    : e.getMessage().contains("already scheduled") ? "ALREADY_SCHEDULED"
                    : e.getMessage().contains("Trial") ? "TRIAL_CANNOT_CANCEL"
                    : "CANCEL_FAILED";
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage(),
                    "errorCode", errorCode));
        }
    }

    // ── POST /api/subscriptions/undo-cancel ──────────────────────────────────
    /**
     * Reverses a scheduled cancellation while it is still pending.
     * Only valid when status == CANCEL_SCHEDULED and cancelEffectiveAt is in the future.
     */
    @PostMapping("/undo-cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> undoCancellation(@AuthenticationPrincipal User caller) {
        Long tenantId = TenantContext.getCurrentTenantId();
        try {
            Tenant saved = subscriptionService.undoCancellation(tenantId, caller);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cancellation reversed. Your subscription will renew as scheduled.",
                    "status", saved.getStatus()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage(),
                    "errorCode", "UNDO_CANCEL_FAILED"));
        }
    }

    // ── Cancellation requests (reviewed by Super Admin) ──────────────────────

    /** Agency admin's current/most recent cancellation request, if any. */
    @GetMapping("/cancellation-requests/mine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> myCancellationRequest() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<CancellationRequest> requests = cancellationRequestRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cancellation request status loaded successfully",
                "data", requests.isEmpty() ? null : toMap(requests.get(0))
        ));
    }

    /**
     * Agency admin requests cancellation — this does NOT cancel the
     * subscription immediately. A Super Admin must approve it first, so
     * cancellation always has a clear state transition and an audit trail.
     */
    @PostMapping("/cancellation-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> requestCancellation(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal User caller) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        if (!cancellationRequestRepository.findAllByTenantIdAndStatus(tenantId, CancellationRequestStatus.PENDING).isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "message", "You already have a pending cancellation request.",
                    "errorCode", "CANCELLATION_REQUEST_PENDING"));
        }

        CancellationReason reason;
        try {
            reason = CancellationReason.valueOf(String.valueOf(body.get("reason")));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "A valid cancellation reason is required."));
        }
        if (!Boolean.TRUE.equals(body.get("confirm"))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Please confirm the cancellation request."));
        }

        CancellationRequest request = cancellationRequestRepository.save(CancellationRequest.builder()
                .tenant(tenant)
                .requestedByUserId(caller.getId())
                .reason(reason)
                .feedback(body.get("feedback") != null ? String.valueOf(body.get("feedback")) : null)
                .status(CancellationRequestStatus.PENDING)
                .build());

        auditLogRepository.save(AuditLog.builder()
                .action("SUBSCRIPTION_CANCELLATION_REQUESTED")
                .entityType("TENANT_" + tenant.getId())
                .entityId(tenant.getId())
                .description("Reason: " + reason)
                .performedBy(caller.getEmail())
                .tenantId(tenant.getId())
                .isSuccess(true)
                .build());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Your cancellation request has been submitted for review.",
                "data", toMap(request)
        ));
    }

    private Map<String, Object> toMap(CancellationRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", request.getId());
        map.put("reason", request.getReason());
        map.put("feedback", request.getFeedback());
        map.put("status", request.getStatus());
        map.put("reviewedBy", request.getReviewedBy());
        map.put("reviewedAt", request.getReviewedAt());
        map.put("reviewNote", request.getReviewNote());
        map.put("createdAt", request.getCreatedAt());
        return map;
    }
}
