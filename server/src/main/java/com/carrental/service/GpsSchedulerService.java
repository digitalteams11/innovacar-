package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.repository.GpsSettingsRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled service that runs geofence and offline checks for all tenants
 * that have GPS actively enabled.
 *
 * <p>Runs every 60 seconds by default. The actual effective polling interval
 * per tenant is governed by the configurable {@code pollingIntervalSec} field
 * stored in {@link GpsSettings} — but since we check once per minute and the
 * minimum recommended polling interval is 15 seconds, this is fine for
 * production usage at reasonable fleet sizes.
 *
 * <p>Each tenant iteration sets {@link TenantContext} so that any service
 * called inside the loop can use the normal tenant-scoped repositories.
 * The context is always cleared in a {@code finally} block to avoid leakage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GpsSchedulerService {

    private final GpsSettingsRepository gpsSettingsRepository;
    private final VehicleRepository vehicleRepository;
    private final GpsGeofenceService gpsGeofenceService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void runPeriodicChecks() {
        List<GpsSettings> activeConfigs = gpsSettingsRepository.findAllByEnabledTrue();
        if (activeConfigs.isEmpty()) return;

        log.debug("GPS scheduler: checking {} tenant(s)", activeConfigs.size());

        for (GpsSettings settings : activeConfigs) {
            Tenant tenant = settings.getTenant();
            if (tenant == null) continue;

            try {
                TenantContext.setCurrentTenantId(tenant.getId());
                checkTenant(settings, tenant);
            } catch (Exception e) {
                log.error("GPS scheduler error for tenant {}: {}", tenant.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void checkTenant(GpsSettings settings, Tenant tenant) {
        List<Vehicle> gpsVehicles = vehicleRepository.findAllByTenantIdAndGpsEnabledTrue(tenant.getId());
        if (gpsVehicles.isEmpty()) return;

        boolean anyChanged = false;
        LocalDateTime now = LocalDateTime.now();

        for (Vehicle vehicle : gpsVehicles) {
            GpsDeviceStatus previousStatus = vehicle.getGpsStatus();

            // 1. Mark as OFFLINE if no update received within the timeout window
            if (vehicle.getLastGpsUpdate() != null) {
                int timeoutMin = settings.getInactivityTimeoutMin() != null
                        ? settings.getInactivityTimeoutMin() : 30;
                if (vehicle.getLastGpsUpdate().isBefore(now.minusMinutes(timeoutMin))
                        && vehicle.getGpsStatus() != GpsDeviceStatus.OFFLINE) {
                    vehicle.setGpsStatus(GpsDeviceStatus.OFFLINE);
                    anyChanged = true;
                }
            }

            // 2. Geofence check — updates vehicle.outOfZone and creates alert
            boolean geofenceChanged = gpsGeofenceService.checkGeofence(vehicle, settings, tenant);
            if (geofenceChanged) anyChanged = true;

            // 3. Offline alert
            gpsGeofenceService.checkOffline(vehicle, settings, tenant);

            // 4. Movement alert (state transition: was stopped/idle, now moving)
            gpsGeofenceService.checkMovement(vehicle, settings, tenant, previousStatus);
        }

        if (anyChanged) {
            vehicleRepository.saveAll(gpsVehicles);
        }
    }
}
