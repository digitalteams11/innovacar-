package com.carrental.controller;

import com.carrental.dto.reservation.CreateReservationRequest;
import com.carrental.dto.reservation.ReservationResponse;
import com.carrental.dto.ApiResponse;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.User;
import com.carrental.service.ReservationService;
import com.carrental.service.PlanLimitService;
import com.carrental.service.VehicleStatusSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
    private final PlanLimitService   planLimitService;
    private final VehicleStatusSyncService vehicleStatusSyncService;
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("RESERVATIONS ROUTE WORKS");
    }
    // -- GET /api/reservations ────────────────────────────────────────────────

    /**
     * Lists all reservations for the caller's tenant.
     * Optional filter by start and end dates.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> listReservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long vehicleId) {
        try {
            reservationService.syncReservationsFromContractsForCurrentTenant();
        } catch (Exception ex) {
            // A best-effort legacy repair must never make the primary list
            // endpoint look missing/broken to the dashboard.
            org.slf4j.LoggerFactory.getLogger(ReservationController.class)
                    .warn("Skipping reservation backfill before list: {}", ex.getMessage());
        }
        try {
            vehicleStatusSyncService.expireOldReservations(com.carrental.security.TenantContext.getCurrentTenantId());
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(ReservationController.class)
                    .warn("Skipping reservation expiry before list: {}", ex.getMessage());
        }
        List<ReservationResponse> reservations;
        try {
            reservations = reservationService.getReservations(startDate, endDate, status, search, page, size, vehicleId);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(ReservationController.class)
                    .warn("Failed to load reservations: {}", ex.getMessage());
            reservations = java.util.List.of();
        }
        String message = reservations.isEmpty() ? "No reservations found." : "Reservations loaded successfully.";
        return ResponseEntity.ok(ApiResponse.success(reservations, message));
    }

    // ── POST /api/reservations/sync-from-contracts ───────────────────────────

    /**
     * Manual repair endpoint: backfills a reservation for any contract in the
     * caller's tenant that doesn't already have one linked. Safe to call repeatedly.
     */
    @PostMapping("/sync-from-contracts")
    @PreAuthorize("@rolePermissionService.has('MANAGE_EMPLOYEES')")
    public ResponseEntity<Map<String, Object>> syncFromContracts() {
        reservationService.syncReservationsFromContractsForCurrentTenant();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Reservations synced from contracts successfully"
        ));
    }
    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, Object>> listUpcomingReservations() {
        List<ReservationResponse> all;
        try {
            all = reservationService.getReservations(null, null, null, null, null, null, null);
        } catch (Exception ex) {
            all = java.util.List.of();
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        List<ReservationResponse> upcoming = all.stream()
                .filter(r -> r.getDateStart() != null && !r.getDateStart().isBefore(today))
                .filter(r -> r.getStatus() != null
                        && !r.getStatus().equals("CANCELLED")
                        && !r.getStatus().equals("COMPLETED"))
                .sorted(java.util.Comparator.comparing(ReservationResponse::getDateStart))
                .limit(20)
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "data", upcoming, "total", upcoming.size()));
    }

    // -- GET /api/reservations/{id} ───────────────────────────────────────────

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
        planLimitService.assertReservationLimit();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createReservation(request));
    }

    // ── DELETE /api/reservations/{id} ────────────────────────────────────────

    /**
     * Soft-deletes a reservation (moves to trash). Requires CANCEL_RESERVATION permission.
     * Returns 409 if the reservation is linked to an active non-trashed contract.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('CANCEL_RESERVATION')")
    public ResponseEntity<Map<String, Object>> deleteReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        String deletedBy = currentUser != null ? currentUser.getEmail() : "unknown";
        try {
            Map<String, Object> data = reservationService.deleteReservation(id, deletedBy);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "Reservation moved to Trash.");
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.startsWith("RESERVATION_LINKED_TO_ACTIVE_CONTRACT")) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("errorCode", "RESERVATION_LINKED_TO_ACTIVE_CONTRACT");
                err.put("message", "Cannot delete this reservation because it is linked to an active contract. Delete or cancel the contract first.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
            }
            throw ex;
        }
    }

    // ── GET /api/reservations/trash ──────────────────────────────────────────

    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> listTrash() {
        List<ReservationResponse> trashed = reservationService.getDeletedReservations();
        return ResponseEntity.ok(ApiResponse.success(trashed,
                trashed.isEmpty() ? "Reservation trash is empty." : "Trashed reservations loaded."));
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
