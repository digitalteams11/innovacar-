package com.carrental.controller;

import com.carrental.dto.reservation.CreateReservationRequest;
import com.carrental.dto.reservation.ReservationResponse;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }
}
