package com.carrental.controller;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Payment REST controller.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ── GET /api/payments/reservation/{reservationId} ────────────────────────

    /**
     * Retrieves the payment details for a specific reservation.
     */
    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentByReservation(reservationId));
    }

    // ── PATCH /api/payments/reservation/{reservationId}/pay ──────────────────

    /**
     * Marks a payment as paid.
     * ADMIN-only operation.
     */
    @PatchMapping("/reservation/{reservationId}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> markAsPaid(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.markAsPaid(reservationId));
    }
}
