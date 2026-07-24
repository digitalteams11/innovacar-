package com.carrental.service;

import com.carrental.dto.vehicle.CreateVehicleRequest;
import com.carrental.dto.vehicle.UpdateVehicleRequest;
import com.carrental.dto.vehicle.VehicleResponse;
import com.carrental.entity.Tenant;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vehicle-management business logic.
 *
 * <p><strong>Tenant isolation:</strong> every query is scoped to the
 * {@code tenantId} extracted from the JWT via {@link TenantContext}.
 * A user of tenant A will always receive a 404 for vehicles that
 * belong to tenant B — preventing both data leakage and enumeration.
 *
 * <p><strong>Access policy (enforced at controller level):</strong>
 * Any authenticated user may read vehicles. Only ADMIN users may
 * create, update, or delete them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final TenantRepository  tenantRepository;

    @Value("${app.vehicles.trash-retention-days:${app.trash.retention-days:30}}")
    private int trashRetentionDays;

    private static final java.util.Set<VehicleStatus> MAINTENANCE_STATUSES =
            java.util.Set.of(VehicleStatus.IN_MAINTENANCE, VehicleStatus.MAINTENANCE, VehicleStatus.OUT_OF_SERVICE);

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all vehicles for the caller's tenant.
     * Optional {@code statut} filter is applied when non-null.
     */
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles(VehicleStatus statut) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            log.warn("Vehicle list requested without tenant context; returning empty list");
            return List.of();
        }
        log.debug("Listing vehicles for tenant [{}], filter statut={}", tenantId, statut);

        List<Vehicle> vehicles = (statut != null)
                ? vehicleRepository.findAllByTenantIdAndStatut(tenantId, statut)
                : vehicleRepository.findAllByTenantId(tenantId);

        return vehicles.stream().map(VehicleResponse::from).toList();
    }

    /**
     * Fetches a single vehicle scoped to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the vehicle does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id) {
        return VehicleResponse.from(fetchVehicleInTenant(id));
    }

    /**
     * Returns the count of AVAILABLE vehicles for the caller's tenant.
     * Useful for a fleet-availability dashboard widget.
     */
    @Transactional(readOnly = true)
    public long countAvailable() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return 0;
        return vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.AVAILABLE);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Adds a new vehicle to the caller's tenant fleet. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the tenant record cannot be found
     */
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        Long   tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        // Default statut to AVAILABLE when caller omits it
        VehicleStatus statut = request.getStatut() != null
                ? request.getStatut()
                : VehicleStatus.AVAILABLE;

        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .marque(request.getMarque())
                .prixJour(request.getPrixJour())
                .statut(statut)
                .category(request.getCategory())
                .plate(request.getPlate())
                .fuel(request.getFuel())
                .transmission(request.getTransmission())
                .seatCount(request.getSeatCount())
                .imageUrl(request.getImageUrl())
                .gpsDeviceId(request.getGpsDeviceId())
                .gpsImei(request.getGpsImei())
                .gpsEnabled(request.getGpsEnabled() != null ? request.getGpsEnabled() : false)
                .tenant(tenant)
                .build());

        log.info("Created vehicle [id={}] '{}' statut={} in tenant [{}]",
                vehicle.getId(), vehicle.getMarque(), vehicle.getStatut(), tenantId);

        return VehicleResponse.from(vehicle);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * ADMIN-only.
     *
     * @throws ResourceNotFoundException if the vehicle is not found in this tenant
     */
    @Transactional
    public VehicleResponse updateVehicle(Long id, UpdateVehicleRequest request) {
        Vehicle vehicle = fetchVehicleInTenant(id);

        if (StringUtils.hasText(request.getMarque())) {
            vehicle.setMarque(request.getMarque());
        }
        if (request.getPrixJour() != null) {
            vehicle.setPrixJour(request.getPrixJour());
        }
        if (request.getStatut() != null) {
            vehicle.setStatut(request.getStatut());
        }
        if (request.getCategory() != null) {
            vehicle.setCategory(request.getCategory());
        }
        if (request.getPlate() != null) {
            vehicle.setPlate(request.getPlate());
        }
        if (request.getFuel() != null) {
            vehicle.setFuel(request.getFuel());
        }
        if (request.getTransmission() != null) {
            vehicle.setTransmission(request.getTransmission());
        }
        if (request.getSeatCount() != null) {
            vehicle.setSeatCount(request.getSeatCount());
        }
        if (request.getImageUrl() != null) {
            vehicle.setImageUrl(request.getImageUrl());
        }
        if (request.getGpsDeviceId() != null) {
            vehicle.setGpsDeviceId(request.getGpsDeviceId());
        }
        if (request.getGpsImei() != null) {
            vehicle.setGpsImei(request.getGpsImei());
        }
        if (request.getGpsEnabled() != null) {
            vehicle.setGpsEnabled(request.getGpsEnabled());
        }
        if (request.getLastLatitude() != null) {
            vehicle.setLastLatitude(request.getLastLatitude());
        }
        if (request.getLastLongitude() != null) {
            vehicle.setLastLongitude(request.getLastLongitude());
        }
        if (request.getLastSpeed() != null) {
            vehicle.setLastSpeed(request.getLastSpeed());
        }
        if (request.getGpsStatus() != null) {
            vehicle.setGpsStatus(request.getGpsStatus());
        }


        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Updated vehicle [id={}] in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return VehicleResponse.from(saved);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Soft-deletes a vehicle from the caller's tenant fleet. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the vehicle is not found in this tenant
     * @throws IllegalStateException     if the vehicle is currently RENTED
     */
    @Transactional
    public Map<String, Object> deleteVehicle(Long id) {
        Vehicle vehicle = fetchVehicleInTenant(id);

        if (vehicle.getStatut() == VehicleStatus.RENTED) {
            throw new IllegalStateException(
                    "Cannot delete vehicle [id=" + id + "] — it is currently RENTED.");
        }

        VehicleStatus statusBeforeDelete = vehicle.getStatut();
        LocalDateTime deletedAt = LocalDateTime.now();
        vehicle.setStatusBeforeDelete(statusBeforeDelete);
        // Removed from the active fleet — OUT_OF_SERVICE stays within the
        // vehicles_statut_check constraint and keeps the vehicle out of any
        // status-based filter even on a path that bypasses @SQLRestriction.
        vehicle.setStatut(VehicleStatus.OUT_OF_SERVICE);
        vehicle.setDeleted(true);
        vehicle.setDeletedAt(deletedAt);
        vehicle.setDeletedBy(currentUserEmail());
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Moved vehicle [id={}] to trash in tenant [{}]",
                id, TenantContext.getCurrentTenantId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("deleted", true);
        result.put("deletedAt", deletedAt);
        result.put("restorableUntil", deletedAt.plusDays(trashRetentionDays));
        return result;
    }

    // ── TRASH / RESTORE ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrashedVehicles() {
        Long tenantId = TenantContext.getCurrentTenantId();
        LocalDateTime now = LocalDateTime.now();
        return vehicleRepository.findAllTrashedByTenantId(tenantId).stream()
                .filter(v -> v.getDeletedAt() != null && v.getDeletedAt().plusDays(trashRetentionDays).isAfter(now))
                .map(this::toTrashItem)
                .toList();
    }

    private Map<String, Object> toTrashItem(Vehicle vehicle) {
        LocalDateTime deletedAt = vehicle.getDeletedAt();
        LocalDateTime restorableUntil = deletedAt.plusDays(trashRetentionDays);
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), restorableUntil));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", vehicle.getId());
        item.put("marque", vehicle.getMarque());
        item.put("plate", vehicle.getPlate());
        item.put("statut", vehicle.getStatut());
        item.put("deletedAt", deletedAt);
        item.put("deletedBy", vehicle.getDeletedBy());
        item.put("restorableUntil", restorableUntil);
        item.put("daysRemaining", daysRemaining);
        return item;
    }

    @Transactional
    public Map<String, Object> restoreVehicle(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Vehicle vehicle = vehicleRepository.findDeletedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Trashed vehicle not found with id: " + id));

        LocalDateTime deletedAt = vehicle.getDeletedAt();
        LocalDateTime restorableUntil = deletedAt != null ? deletedAt.plusDays(trashRetentionDays) : null;
        if (restorableUntil == null || LocalDateTime.now().isAfter(restorableUntil)) {
            throw new IllegalStateException(
                    "This vehicle's " + trashRetentionDays
                            + "-day restore window has expired and it can no longer be restored.");
        }

        VehicleStatus previous = vehicle.getStatusBeforeDelete();
        VehicleStatus restoredStatus = previous != null && MAINTENANCE_STATUSES.contains(previous)
                ? previous : VehicleStatus.AVAILABLE;
        vehicle.setStatut(restoredStatus);
        vehicle.setStatusBeforeDelete(null);
        vehicle.setDeleted(false);
        vehicle.setDeletedAt(null);
        vehicle.setDeletedBy(null);
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Restored vehicle [id={}] in tenant [{}] to status [{}]", id, tenantId, restoredStatus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("deleted", false);
        result.put("statut", saved.getStatut());
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tenant-scoped vehicle lookup. Returns 404 for both missing and
     * cross-tenant vehicles so tenant B cannot discover tenant A's IDs.
     */
    private Vehicle fetchVehicleInTenant(Long vehicleId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle not found with id: " + vehicleId));
    }

    private String currentUserEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
