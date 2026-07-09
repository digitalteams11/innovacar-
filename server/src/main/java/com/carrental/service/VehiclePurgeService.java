package com.carrental.service;

import com.carrental.entity.Vehicle;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.exception.VehicleStillReferencedException;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Permanently deletes a trashed vehicle. Used both by the manual "Delete
 * permanently" action and by {@link VehicleTrashCleanupJob}'s daily
 * auto-purge.
 *
 * <p>Unlike contracts, a vehicle is never purged while any contract or
 * reservation row still references it (even a trashed-but-not-yet-purged
 * contract) — those FK rows would otherwise violate the database's foreign
 * key constraint, and historical rental records must never be silently
 * orphaned. If referenced, the vehicle stays soft-deleted and a clear
 * message is returned/logged instead. Clients, contracts, tenants and users
 * are never touched by this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehiclePurgeService {

    private final VehicleRepository vehicleRepository;
    private final ContractRepository contractRepository;
    private final ReservationRepository reservationRepository;

    /** Manual purge — only ever targets a vehicle already in trash for this tenant. */
    @Transactional
    public Map<String, Object> purgeVehicle(Long vehicleId, Long tenantId) {
        Vehicle vehicle = vehicleRepository.findDeletedByIdAndTenantId(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Trashed vehicle not found with id: " + vehicleId));
        return purge(vehicle);
    }

    /** Core purge logic, also called per-vehicle by the daily auto-purge job. */
    @Transactional
    public Map<String, Object> purge(Vehicle vehicle) {
        Long id = vehicle.getId();
        String plate = vehicle.getPlate();

        if (contractRepository.existsAnyContractForVehicleId(id) || reservationRepository.existsByVehicleId(id)) {
            throw new VehicleStillReferencedException(
                    "Vehicle [id=" + id + "] is still referenced by a contract or reservation and cannot be "
                            + "permanently deleted. It remains in Trash.");
        }

        try {
            vehicleRepository.delete(vehicle);
            // Force the DELETE to execute now, inside this try, instead of at a
            // later flush/commit point outside this method's catch block.
            vehicleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new VehicleStillReferencedException(
                    "Vehicle [id=" + id + "] is still referenced by another record and cannot be permanently "
                            + "deleted. It remains in Trash.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleId", id);
        result.put("plate", plate);
        result.put("purged", true);
        return result;
    }
}
