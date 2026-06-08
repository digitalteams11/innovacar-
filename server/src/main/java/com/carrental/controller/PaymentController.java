package com.carrental.controller;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.dto.payment.PaymentStatsResponse;
import com.carrental.dto.payment.RecordPaymentRequest;
import com.carrental.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Payment REST controller.
 * Core financial transaction API for the platform.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ── GET /api/payments ────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    // ── GET /api/payments/{id} ───────────────────────────────────────────────

    @GetMapping("/{id}")
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
    public ResponseEntity<PaymentStatsResponse> getPaymentStats() {
        return ResponseEntity.ok(paymentService.getPaymentStats());
    }

    // ── GET /api/payments/monthly-revenue ────────────────────────────────────

    @GetMapping("/monthly-revenue")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyRevenue() {
        return ResponseEntity.ok(paymentService.getMonthlyRevenue());
    }

    // ── GET /api/payments/client/{clientId} ──────────────────────────────────

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<PaymentResponse>> getClientPaymentHistory(@PathVariable Long clientId) {
        return ResponseEntity.ok(paymentService.getClientPaymentHistory(clientId));
    }

    // ── GET /api/payments/contract/{contractId} ──────────────────────────────

    @GetMapping("/contract/{contractId}")
    public ResponseEntity<List<PaymentResponse>> getContractPayments(@PathVariable Long contractId) {
        return ResponseEntity.ok(paymentService.getContractPayments(contractId));
    }

    // ── GET /api/payments/reservation/{reservationId} ────────────────────────

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentByReservation(reservationId));
    }

    // ── PATCH /api/payments/reservation/{reservationId}/pay ──────────────────

    @PatchMapping("/reservation/{reservationId}/pay")
    @PreAuthorize("@rolePermissionService.has('RECORD_PAYMENT')")
    public ResponseEntity<PaymentResponse> markReservationPaymentAsPaid(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.markAsPaid(reservationId));
    }
}
