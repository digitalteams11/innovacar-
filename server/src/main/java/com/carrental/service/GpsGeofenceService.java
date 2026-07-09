package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.repository.GpsAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Geofence detection and alert creation service.
 *
 * <p>All alert-creation methods are guarded by a deduplication window: if an
 * identical alert (same type + vehicle) was already created within the last
 * {@value #DEDUP_HOURS} hours, the new one is silently skipped. This prevents
 * alert spam on every polling cycle when a vehicle stays in the same condition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GpsGeofenceService {

    private static final int DEDUP_HOURS = 1;

    private final GpsAlertRepository gpsAlertRepository;

    // ── Haversine distance formula ───────────────────────────────────────────

    /**
     * Returns the great-circle distance in kilometres between two coordinates
     * using the Haversine formula.
     */
    public double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // ── Alert checks ─────────────────────────────────────────────────────────

    /**
     * Checks whether the vehicle's position is inside or outside the configured
     * city radius, updates {@code vehicle.outOfZone}, and creates a geofence
     * alert if the state changed.
     *
     * @return true if the vehicle's out-of-zone state changed (caller should save)
     */
    @Transactional
    public boolean checkGeofence(Vehicle vehicle, GpsSettings settings, Tenant tenant) {
        if (!Boolean.TRUE.equals(settings.getNotifyGeofence())) return false;
        if (settings.getCityLat() == null || settings.getCityLng() == null
                || settings.getRadiusKm() == null) return false;
        if (vehicle.getLastLatitude() == null || vehicle.getLastLongitude() == null) return false;

        double distance = calculateDistanceKm(
                settings.getCityLat(), settings.getCityLng(),
                vehicle.getLastLatitude(), vehicle.getLastLongitude());

        boolean nowOutOfZone = distance > settings.getRadiusKm();
        boolean wasOutOfZone = Boolean.TRUE.equals(vehicle.getOutOfZone());

        if (nowOutOfZone && !wasOutOfZone) {
            vehicle.setOutOfZone(true);
            String msg = vehicle.getMarque() + " (" + vehicle.getPlate() + ") left the allowed city zone"
                    + " — " + String.format("%.1f", distance) + " km from center";
            createAlertIfNew(tenant, GpsAlertType.GEOFENCE_EXIT, msg, "HIGH",
                    vehicle.getId(), vehicle.getMarque(),
                    vehicle.getLastLatitude(), vehicle.getLastLongitude(), vehicle.getLastSpeed());
            return true;
        }

        if (!nowOutOfZone && wasOutOfZone) {
            vehicle.setOutOfZone(false);
            String msg = vehicle.getMarque() + " (" + vehicle.getPlate() + ") returned to the allowed city zone";
            createAlertIfNew(tenant, GpsAlertType.GEOFENCE_ENTER, msg, "MEDIUM",
                    vehicle.getId(), vehicle.getMarque(),
                    vehicle.getLastLatitude(), vehicle.getLastLongitude(), vehicle.getLastSpeed());
            return true;
        }

        return false;
    }

    /**
     * Creates an OFFLINE alert if the vehicle has not reported a GPS update for
     * longer than the configured inactivity timeout.
     */
    @Transactional
    public void checkOffline(Vehicle vehicle, GpsSettings settings, Tenant tenant) {
        if (!Boolean.TRUE.equals(settings.getNotifyOffline())) return;
        if (vehicle.getLastGpsUpdate() == null) return;
        if (vehicle.getGpsStatus() == GpsDeviceStatus.OFFLINE) return; // already flagged

        int timeoutMin = settings.getInactivityTimeoutMin() != null
                ? settings.getInactivityTimeoutMin() : 30;
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMin);

        if (vehicle.getLastGpsUpdate().isBefore(cutoff)) {
            String msg = vehicle.getMarque() + " (" + vehicle.getPlate() + ") has been offline"
                    + " for more than " + timeoutMin + " minutes";
            createAlertIfNew(tenant, GpsAlertType.OFFLINE, msg, "MEDIUM",
                    vehicle.getId(), vehicle.getMarque(),
                    vehicle.getLastLatitude(), vehicle.getLastLongitude(), null);
        }
    }

    /**
     * Creates a VEHICLE_STARTED_MOVING alert when the vehicle transitions from
     * STOPPED/IDLE → MOVING.
     */
    @Transactional
    public void checkMovement(Vehicle vehicle, GpsSettings settings, Tenant tenant,
                               GpsDeviceStatus previousStatus) {
        if (!Boolean.TRUE.equals(settings.getNotifyMovement())) return;
        if (vehicle.getGpsStatus() != GpsDeviceStatus.MOVING) return;
        if (previousStatus == GpsDeviceStatus.MOVING) return; // was already moving

        String speed = (vehicle.getLastSpeed() != null && vehicle.getLastSpeed() > 0)
                ? " at " + vehicle.getLastSpeed().intValue() + " km/h" : "";
        String msg = vehicle.getMarque() + " (" + vehicle.getPlate() + ") started moving" + speed;
        createAlertIfNew(tenant, GpsAlertType.VEHICLE_STARTED_MOVING, msg, "LOW",
                vehicle.getId(), vehicle.getMarque(),
                vehicle.getLastLatitude(), vehicle.getLastLongitude(), vehicle.getLastSpeed());
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void createAlertIfNew(Tenant tenant, GpsAlertType type, String message, String severity,
                                   Long vehicleId, String vehicleName,
                                   Double latitude, Double longitude, Double speed) {
        LocalDateTime deduplicationWindow = LocalDateTime.now().minusHours(DEDUP_HOURS);
        boolean alreadyExists = gpsAlertRepository
                .existsByTenantIdAndAlertTypeAndVehicleIdAndCreatedAtAfter(
                        tenant.getId(), type, vehicleId, deduplicationWindow);

        if (alreadyExists) {
            log.debug("GPS alert deduped: type={} vehicle={} tenant={}", type, vehicleId, tenant.getId());
            return;
        }

        GpsAlert alert = GpsAlert.builder()
                .alertType(type)
                .message(message)
                .severity(severity)
                .read(false)
                .vehicleId(vehicleId)
                .vehicleName(vehicleName)
                .latitude(latitude)
                .longitude(longitude)
                .speed(speed)
                .tenant(tenant)
                .build();

        gpsAlertRepository.save(alert);
        log.info("GPS alert created: type={} vehicle={} tenant={}", type, vehicleId, tenant.getId());
    }
}
