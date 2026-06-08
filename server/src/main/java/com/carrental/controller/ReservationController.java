package com.carrental.controller;

import com.carrental.dto.reservation.CreateReservationRequest;
import com.carrental.dto.reservation.ReservationResponse;
import com.carrental.entity.ReservationStatus;
import com.carrental.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reservation REST controller.
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    // ── GET /api/reservations ────────────────────────────────────────────────

    /**
     * Lists all reservations for the caller's tenant.
     * Optional filter by start and end dates.
     */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listReservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reservationService.getReservations(startDate, endDate));
    }

    // ── GET /api/reservations/{id} ───────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    // ── POST /api/reservations ───────────────────────────────────────────────

    /**
     * Creates a new reservation. Available to all authenticated users (EMPLOYEE or ADMIN).
     */
    @PostMapping
    @PreAuthorize("@rolePermissionService.has('CREATE_RESERVATION')")
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createReservation(request));
    }

    // ── DELETE /api/reservations/{id} ────────────────────────────────────────

    /**
     * Deletes a reservation. Requires ADMIN role.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('CANCEL_RESERVATION')")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('EDIT_RESERVATION')")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable Long id,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.ok(reservationService.updateReservation(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@rolePermissionService.has('EDIT_RESERVATION')")
    public ResponseEntity<ReservationResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String value = body.get("status");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        return ResponseEntity.ok(reservationService.updateStatus(
                id, ReservationStatus.valueOf(value.trim().toUpperCase())));
    }
}
