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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

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

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all vehicles for the caller's tenant.
     * Optional {@code statut} filter is applied when non-null.
     */
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles(VehicleStatus statut) {
        Long tenantId = TenantContext.getCurrentTenantId();
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
     * Hard-deletes a vehicle from the caller's tenant fleet. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the vehicle is not found in this tenant
     * @throws IllegalStateException     if the vehicle is currently RENTED
     */
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = fetchVehicleInTenant(id);

        if (vehicle.getStatut() == VehicleStatus.RENTED) {
            throw new IllegalStateException(
                    "Cannot delete vehicle [id=" + id + "] — it is currently RENTED.");
        }

        vehicleRepository.delete(vehicle);
        log.info("Deleted vehicle [id={}] from tenant [{}]",
                id, TenantContext.getCurrentTenantId());
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
}
