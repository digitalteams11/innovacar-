package com.carrental.controller;

import com.carrental.dto.vehicle.CreateVehicleRequest;
import com.carrental.dto.vehicle.UpdateVehicleRequest;
import com.carrental.dto.vehicle.VehicleResponse;
import com.carrental.entity.VehicleStatus;
import com.carrental.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(vehicleService.getAllVehicles(statut));
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.createVehicle(request));
    }

    // ── PUT /api/vehicles/{id} ───────────────────────────────────────────────

    /**
     * Partially updates a vehicle. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleResponse> changeStatut(
            @PathVariable Long id,
            @RequestParam VehicleStatus statut) {

        UpdateVehicleRequest req = new UpdateVehicleRequest();
        req.setStatut(statut);
        return ResponseEntity.ok(vehicleService.updateVehicle(id, req));
    }

    // ── DELETE /api/vehicles/{id} ────────────────────────────────────────────

    /**
     * Hard-deletes a vehicle. Returns 409 if the vehicle is currently RENTED.
     * ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }
}
