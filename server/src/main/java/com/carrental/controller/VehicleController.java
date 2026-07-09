package com.carrental.controller;

import com.carrental.dto.vehicle.CreateVehicleRequest;
import com.carrental.dto.vehicle.UpdateVehicleRequest;
import com.carrental.dto.vehicle.VehicleResponse;
import com.carrental.entity.VehicleStatus;
import com.carrental.service.AvailabilityService;
import com.carrental.service.PlanLimitService;
import com.carrental.service.VehiclePurgeService;
import com.carrental.service.VehicleService;
import com.carrental.service.VehicleStatusSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vehicle-management REST controller.
 *
 * <pre>
 * GET    /api/vehicles              – list all vehicles (opt. ?statut=X)  [authenticated]
 * GET    /api/vehicles/{id}         – get vehicle by id                   [authenticated]
 * GET    /api/vehicles/count/available – available fleet count            [authenticated]
 * POST   /api/vehicles              – create vehicle                      [ADMIN]
 * PUT    /api/vehicles/{id}         – partial update                      [ADMIN]
 * DELETE /api/vehicles/{id}         – delete vehicle                      [ADMIN]
 * PATCH  /api/vehicles/{id}/statut  – change status only                  [ADMIN]
 * </pre>
 *
 * All endpoints sit behind the {@code JwtAuthenticationFilter} — an invalid or
 * missing token will never reach the controller.
 */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehiclePurgeService vehiclePurgeService;
    private final PlanLimitService planLimitService;
    private final AvailabilityService availabilityService;
    private final VehicleStatusSyncService vehicleStatusSyncService;

    // ── GET /api/vehicles ────────────────────────────────────────────────────

    /**
     * Returns all vehicles in the caller's tenant.
     * Optional query param {@code statut} filters by {@link VehicleStatus}.
     *
     * <p>Example: {@code GET /api/vehicles?statut=AVAILABLE}
     */
    @GetMapping
    public ResponseEntity<List<VehicleResponse>> listVehicles(
            @RequestParam(required = false) VehicleStatus statut) {
        Long tenantId = com.carrental.security.TenantContext.getCurrentTenantId();
        try {
            // Fix stale RESERVED vehicles before returning the list — a stored
            // status is never trusted blindly. Cheap: only touches vehicles
            // currently RESERVED plus reservations that have just expired.
            vehicleStatusSyncService.expireOldReservations(tenantId);
            vehicleStatusSyncService.syncReservedVehicles(tenantId);
        } catch (Exception ex) {
            // Best-effort — must never make the primary vehicle list look broken.
            org.slf4j.LoggerFactory.getLogger(VehicleController.class)
                    .warn("Skipping vehicle status sync before list: {}", ex.getMessage());
        }
        return ResponseEntity.ok(vehicleService.getAllVehicles(statut));
    }

    // ── GET /api/vehicles/available ──────────────────────────────────────────

    /**
     * Returns the list of vehicles available for a given date range.
     * Delegates to AvailabilityService so the selector and direct-create use the same logic.
     * Example: GET /api/vehicles/available?startDate=2026-07-01&endDate=2026-07-05
     */
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableVehicles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "09:00") String startTime,
            @RequestParam(defaultValue = "18:00") String endTime,
            @RequestParam(required = false) Long excludeReservationId) {
        try {
            LocalTime parsedStartTime = LocalTime.parse(startTime);
            LocalTime parsedEndTime = LocalTime.parse(endTime);
            List<Map<String, Object>> vehicles = availabilityService
                    .getAvailableVehicles(startDate, parsedStartTime, endDate, parsedEndTime, excludeReservationId)
                    .stream()
                    .map(v -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", v.getId());
                        m.put("marque", v.getMarque());
                        m.put("plate", v.getPlate());
                        m.put("category", v.getCategory());
                        m.put("statut", v.getStatut());
                        m.put("prixJour", v.getPrixJour());
                        m.put("dailyPrice", v.getPrixJour());
                        return m;
                    })
                    .toList();
            return ResponseEntity.ok(Map.of("success", true, "data", vehicles,
                    "message", vehicles.isEmpty() ? "No available vehicles for selected dates." : "Available vehicles loaded."));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("success", true, "data", List.of(),
                    "message", "No available vehicles for selected dates."));
        }
    }

    // ── GET /api/vehicles/count/available ────────────────────────────────────

    /**
     * Returns the number of AVAILABLE vehicles in the caller's tenant.
     * Useful for dashboard widgets.
     */
    @GetMapping("/count/available")
    public ResponseEntity<Map<String, Long>> countAvailable() {
        return ResponseEntity.ok(Map.of("available", vehicleService.countAvailable()));
    }

    // ── GET /api/vehicles/{id} ───────────────────────────────────────────────

    /**
     * Fetches a single vehicle. Returns 404 for vehicles belonging to other
     * tenants (prevents cross-tenant enumeration).
     */
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicle(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }

    // ── POST /api/vehicles ───────────────────────────────────────────────────

    /**
     * Registers a new vehicle in the caller's tenant fleet. ADMIN-only.
     */
    @PostMapping
    @PreAuthorize("@rolePermissionService.has('CREATE_VEHICLE')")
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request) {
        planLimitService.assertVehicleLimit();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.createVehicle(request));
    }

    // ── PUT /api/vehicles/{id} ───────────────────────────────────────────────

    /**
     * Partially updates a vehicle. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('EDIT_VEHICLE')")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, request));
    }

    // ── PATCH /api/vehicles/{id}/statut ──────────────────────────────────────

    /**
     * Convenience endpoint to change only the vehicle's status.
     * ADMIN-only. Especially useful for the rental workflow (AVAILABLE → RENTED).
     */
    @PatchMapping("/{id}/statut")
    @PreAuthorize("@rolePermissionService.has('EDIT_VEHICLE')")
    public ResponseEntity<?> changeStatut(
            @PathVariable Long id,
            @RequestParam VehicleStatus statut) {

        // Maintenance status must always be backed by a VehicleMaintenance work order.
        // Directly setting MAINTENANCE bypasses the maintenance module and leaves the
        // vehicle with no linked work order, causing the Maintenance page to show zero.
        if (statut == VehicleStatus.MAINTENANCE) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "Vehicle maintenance status must be managed through the Maintenance module. "
                    + "Create a work order from the Maintenance page to put a vehicle into maintenance.");
            err.put("errorCode", "USE_MAINTENANCE_MODULE");
            err.put("data", null);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
        }

        UpdateVehicleRequest req = new UpdateVehicleRequest();
        req.setStatut(statut);
        return ResponseEntity.ok(vehicleService.updateVehicle(id, req));
    }

    // ── DELETE /api/vehicles/{id} ────────────────────────────────────────────

    /**
     * Moves a vehicle to Trash (soft delete). Returns 409 if the vehicle is
     * currently RENTED. ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('DELETE_VEHICLE')")
    public ResponseEntity<Map<String, Object>> deleteVehicle(@PathVariable Long id) {
        try {
            Map<String, Object> result = vehicleService.deleteVehicle(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vehicle moved to trash.",
                    "data", result
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Vehicle not found or already deleted.", "VEHICLE_NOT_FOUND", Map.of("vehicleId", id)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    ex.getMessage(), "VEHICLE_RENTED", Map.of("vehicleId", id)));
        }
    }

    // ── POST /api/vehicles/sync-statuses ─────────────────────────────────────

    /**
     * Manual repair endpoint: recalculates every vehicle's status for the
     * caller's agency from real blockers (maintenance, contracts, reservations),
     * expiring any stale reservation along the way. Fixes vehicles stuck
     * RESERVED/RENTED/MAINTENANCE after their blocker has actually ended.
     */
    @PostMapping("/sync-statuses")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('AGENCY_OWNER')")
    public ResponseEntity<Map<String, Object>> syncStatuses() {
        Long tenantId = com.carrental.security.TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(
                    "No agency context found for the current user.", "AGENCY_CONTEXT_MISSING", null));
        }
        try {
            Map<String, Object> summary = vehicleStatusSyncService.recalculateAgencyVehicles(tenantId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vehicle statuses synchronized.",
                    "data", summary
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(
                    "Failed to synchronize vehicle statuses.", "STATUS_SYNC_FAILED", null));
        }
    }

    // ── Trash / Restore / Purge ──────────────────────────────────────────────

    @GetMapping("/trash")
    @PreAuthorize("@rolePermissionService.has('VIEW_VEHICLES')")
    public ResponseEntity<Map<String, Object>> listTrashedVehicles() {
        List<Map<String, Object>> trashed = vehicleService.getTrashedVehicles();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Trashed vehicles loaded.",
                "data", trashed
        ));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@rolePermissionService.has('DELETE_VEHICLE')")
    public ResponseEntity<Map<String, Object>> restoreVehicle(@PathVariable Long id) {
        try {
            Map<String, Object> result = vehicleService.restoreVehicle(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vehicle restored successfully.",
                    "data", result
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Trashed vehicle not found.", "VEHICLE_NOT_FOUND", Map.of("vehicleId", id)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    ex.getMessage(), "RESTORE_WINDOW_EXPIRED", Map.of("vehicleId", id)));
        }
    }

    @DeleteMapping("/{id}/purge")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('AGENCY_OWNER')")
    public ResponseEntity<Map<String, Object>> purgeVehicle(@PathVariable Long id) {
        try {
            Long tenantId = com.carrental.security.TenantContext.getCurrentTenantId();
            Map<String, Object> result = vehiclePurgeService.purgeVehicle(id, tenantId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vehicle permanently deleted.",
                    "data", result
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Trashed vehicle not found.", "VEHICLE_NOT_FOUND", Map.of("vehicleId", id)));
        } catch (com.carrental.exception.VehicleStillReferencedException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    ex.getMessage(), "VEHICLE_STILL_REFERENCED", Map.of("vehicleId", id)));
        }
    }

    private Map<String, Object> errorBody(String message, String errorCode, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errorCode", errorCode);
        body.put("data", data);
        return body;
    }
}
