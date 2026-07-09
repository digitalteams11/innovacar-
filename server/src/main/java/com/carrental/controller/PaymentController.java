package com.carrental.controller;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.dto.payment.PaymentStatsResponse;
import com.carrental.dto.payment.RecordPaymentRequest;
import com.carrental.entity.User;
import com.carrental.service.PaymentService;
import com.carrental.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Payment REST controller.
 * Core financial transaction API for the platform.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RolePermissionService rolePermissionService;

    // ── GET /api/payments ────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentResponse>> getAllPayments(Authentication authentication) {
        debugLog(authentication, "PAYMENT_VIEW", "PAYMENTS", "GET /api/payments");
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    // ── GET /api/payments/{id} ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    // ── POST /api/payments ───────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('RECORD_PAYMENT')")
    public ResponseEntity<PaymentResponse> recordPayment(@RequestBody RecordPaymentRequest request) {
        return ResponseEntity.ok(paymentService.recordPayment(request));
    }

    // ── POST /api/payments/{id}/refund ───────────────────────────────────────

    @PostMapping("/{id}/refund")
    @PreAuthorize("@rolePermissionService.has('RECORD_PAYMENT')")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = request.get("amount") != null
                ? new BigDecimal(request.get("amount").toString())
                : BigDecimal.ZERO;
        String reason = request.get("reason") != null ? request.get("reason").toString() : "";
        return ResponseEntity.ok(paymentService.refundPayment(id, amount, reason));
    }

    // ── GET /api/payments/stats ──────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_STATS_VIEW')")
    public ResponseEntity<PaymentStatsResponse> getPaymentStats(Authentication authentication) {
        debugLog(authentication, "PAYMENT_STATS_VIEW", "PAYMENTS", "GET /api/payments/stats");
        return ResponseEntity.ok(paymentService.getPaymentStats());
    }

    // ── GET /api/payments/monthly-revenue ────────────────────────────────────

    @GetMapping("/monthly-revenue")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_STATS_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyRevenue(Authentication authentication) {
        debugLog(authentication, "PAYMENT_STATS_VIEW", "PAYMENTS", "GET /api/payments/monthly-revenue");
        return ResponseEntity.ok(paymentService.getMonthlyRevenue());
    }

    // ── GET /api/payments/client/{clientId} ──────────────────────────────────

    @GetMapping("/client/{clientId}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentResponse>> getClientPaymentHistory(@PathVariable Long clientId) {
        return ResponseEntity.ok(paymentService.getClientPaymentHistory(clientId));
    }

    // ── GET /api/payments/contract/{contractId} ──────────────────────────────

    @GetMapping("/contract/{contractId}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentResponse>> getContractPayments(@PathVariable Long contractId) {
        return ResponseEntity.ok(paymentService.getContractPayments(contractId));
    }

    // ── GET /api/payments/reservation/{reservationId} ────────────────────────

    @GetMapping("/reservation/{reservationId}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentByReservation(reservationId));
    }

    // ── PATCH /api/payments/reservation/{reservationId}/pay ──────────────────

    @PatchMapping("/reservation/{reservationId}/pay")
    @PreAuthorize("@rolePermissionService.has('RECORD_PAYMENT')")
    public ResponseEntity<PaymentResponse> markReservationPaymentAsPaid(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.markAsPaid(reservationId));
    }

    // ── Debug helper ──────────────────────────────────────────────────────────

    private void debugLog(Authentication authentication, String requiredPermission, String requiredFeature, String endpoint) {
        if (!log.isInfoEnabled()) return;
        try {
            User user = authentication != null && authentication.getPrincipal() instanceof User u ? u : null;
            boolean hasPermission = rolePermissionService.has(requiredPermission);
            log.info("[PAYMENTS_ACCESS_DEBUG] endpoint={} userId={} email={} role={} agencyId={} requiredPermission={} hasPermission={} requiredFeature={} result={}",
                    endpoint,
                    user != null ? user.getId() : "unknown",
                    user != null ? user.getEmail() : "unknown",
                    user != null && user.getRole() != null ? user.getRole().name() : "unknown",
                    user != null && user.getTenant() != null ? user.getTenant().getId() : "null",
                    requiredPermission,
                    hasPermission,
                    requiredFeature,
                    hasPermission ? "ALLOW" : "DENY");
        } catch (Exception ex) {
            log.debug("[PAYMENTS_ACCESS_DEBUG] failed to log access details: {}", ex.getMessage());
        }
    }
}
