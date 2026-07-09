package com.carrental.service;

import com.carrental.entity.Tenant;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Keeps vehicle statuses honest without waiting for a user to hit the API.
 *
 * <p>Every 5 minutes: expires reservations whose end date/time has passed,
 * then recalculates any vehicle still stored as RESERVED. Deliberately does
 * NOT walk every vehicle in every tenant — only the cheap, targeted subset —
 * so this stays lightweight even on large fleets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleStatusScheduler {

    private final TenantRepository tenantRepository;
    private final VehicleStatusSyncService vehicleStatusSyncService;

    @Scheduled(fixedRate = 300_000)
    public void syncExpiredReservationsAndVehicleStatuses() {
        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) return;

        for (Tenant tenant : tenants) {
            Long tenantId = tenant.getId();
            try {
                TenantContext.setCurrentTenantId(tenantId);
                int expired = vehicleStatusSyncService.expireOldReservations(tenantId);
                int updated = vehicleStatusSyncService.syncReservedVehicles(tenantId);
                if (expired > 0 || updated > 0) {
                    log.info("[VEHICLE_STATUS_SYNC] scheduler tenantId={} expiredReservations={} updatedVehicles={}",
                            tenantId, expired, updated);
                }
            } catch (Exception e) {
                log.error("[VEHICLE_STATUS_SYNC] scheduler error for tenantId={}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
