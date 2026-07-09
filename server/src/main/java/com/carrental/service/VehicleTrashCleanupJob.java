package com.carrental.service;

import com.carrental.entity.Vehicle;
import com.carrental.exception.VehicleStillReferencedException;
import com.carrental.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily job that permanently purges vehicles that have been sitting in
 * trash longer than {@code app.vehicles.trash-retention-days}. A vehicle
 * still referenced by a contract or reservation is skipped (stays in
 * trash) rather than failing the whole run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleTrashCleanupJob {

    private final VehicleRepository vehicleRepository;
    private final VehiclePurgeService vehiclePurgeService;

    @Value("${app.vehicles.trash-retention-days:${app.trash.retention-days:30}}")
    private int trashRetentionDays;

    @Scheduled(cron = "${app.vehicles.trash-purge.cron:0 45 3 * * *}")
    public void purgeExpiredTrash() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(trashRetentionDays);
        List<Vehicle> expired = vehicleRepository.findExpiredTrash(cutoff);
        if (expired.isEmpty()) {
            return;
        }
        log.info("[TRASH_PURGE] starting auto-purge of {} expired trashed vehicle(s)", expired.size());
        for (Vehicle vehicle : expired) {
            Long vehicleId = vehicle.getId();
            LocalDateTime deletedAt = vehicle.getDeletedAt();
            try {
                vehiclePurgeService.purge(vehicle);
                log.info("[TRASH_PURGE] entity=VEHICLE id={} deletedAt={} purgedAt={}",
                        vehicleId, deletedAt, LocalDateTime.now());
            } catch (VehicleStillReferencedException ex) {
                log.info("[TRASH_PURGE] entity=VEHICLE id={} skipped — still referenced by a contract or reservation",
                        vehicleId);
            } catch (Exception ex) {
                log.error("[TRASH_PURGE] entity=VEHICLE id={} failed to purge: {}", vehicleId, ex.getMessage(), ex);
            }
        }
    }
}
