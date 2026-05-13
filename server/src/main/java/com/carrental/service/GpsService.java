package com.carrental.service;

import com.carrental.dto.gps.*;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.GpsAlertRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GPS tracking business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GpsService {

    private final VehicleRepository vehicleRepository;
    private final GpsAlertRepository gpsAlertRepository;

    // ── READ: Live positions ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GpsVehicleResponse> getLivePositions() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return vehicleRepository.findAllByTenantId(tenantId).stream()
                .map(GpsVehicleResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GpsVehicleResponse> getTrackedVehicles() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return vehicleRepository.findAllByTenantIdAndGpsEnabledTrue(tenantId).stream()
                .map(GpsVehicleResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GpsVehicleResponse getVehiclePosition(Long vehicleId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        return GpsVehicleResponse.from(vehicle);
    }

    // ── READ: Dashboard stats ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GpsDashboardStats getDashboardStats() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Vehicle> tracked = vehicleRepository.findAllByTenantIdAndGpsEnabledTrue(tenantId);

        long online = tracked.stream().filter(v -> v.getGpsStatus() == GpsDeviceStatus.ONLINE).count();
        long offline = tracked.stream().filter(v -> v.getGpsStatus() == GpsDeviceStatus.OFFLINE).count();
        long moving = tracked.stream().filter(v -> v.getGpsStatus() == GpsDeviceStatus.MOVING).count();
        long stopped = tracked.stream().filter(v -> v.getGpsStatus() == GpsDeviceStatus.STOPPED).count();
        long idle = tracked.stream().filter(v -> v.getGpsStatus() == GpsDeviceStatus.IDLE).count();
        long alerts = gpsAlertRepository.countByTenantIdAndReadFalse(tenantId);

        return GpsDashboardStats.builder()
                .totalTracked((long) tracked.size())
                .online(online)
                .offline(offline)
                .moving(moving)
                .stopped(stopped)
                .idle(idle)
                .activeAlerts(alerts)
                .totalDistanceTodayKm(0.0) // Would calculate from history
                .build();
    }

    // ── READ: History (mock for now, would query GPS provider) ───────────────

    @Transactional(readOnly = true)
    public List<GpsHistoryResponse> getVehicleHistory(Long vehicleId, LocalDateTime from, LocalDateTime to) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        if (vehicle.getGpsDeviceId() == null) {
            return List.of();
        }

        // For now, return a mock route based on last known position
        // In production, this would query the GPS provider's history API
        Double lat = vehicle.getLastLatitude();
        Double lng = vehicle.getLastLongitude();

        if (lat == null || lng == null) {
            return List.of();
        }

        // Generate a small mock route around the last known position
        return List.of(
                GpsHistoryResponse.builder()
                        .latitude(lat + 0.001)
                        .longitude(lng + 0.001)
                        .speed(45.0)
                        .timestamp(from.plusMinutes(0))
                        .status("MOVING")
                        .address("Route start")
                        .build(),
                GpsHistoryResponse.builder()
                        .latitude(lat + 0.0005)
                        .longitude(lng + 0.0003)
                        .speed(60.0)
                        .timestamp(from.plusMinutes(5))
                        .status("MOVING")
                        .address("En route")
                        .build(),
                GpsHistoryResponse.builder()
                        .latitude(lat)
                        .longitude(lng)
                        .speed(0.0)
                        .timestamp(from.plusMinutes(10))
                        .status("STOPPED")
                        .address("Current position")
                        .build()
        );
    }

    // ── READ: Trips (mock) ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GpsTripResponse> getTrips(Long vehicleId, LocalDateTime from, LocalDateTime to) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        if (vehicle.getGpsDeviceId() == null || vehicle.getLastLatitude() == null) {
            return List.of();
        }

        // Mock trip data
        return List.of(
                GpsTripResponse.builder()
                        .vehicleId(vehicle.getId())
                        .vehicleName(vehicle.getMarque())
                        .startTime(from)
                        .endTime(from.plusHours(2))
                        .startLatitude(vehicle.getLastLatitude() + 0.01)
                        .startLongitude(vehicle.getLastLongitude() + 0.01)
                        .endLatitude(vehicle.getLastLatitude())
                        .endLongitude(vehicle.getLastLongitude())
                        .distanceKm(12.5)
                        .maxSpeed(80.0)
                        .avgSpeed(45.0)
                        .durationMinutes(120L)
                        .startAddress("Start location")
                        .endAddress("End location")
                        .build()
        );
    }

    // ── READ: Alerts ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GpsAlertResponse> getAlerts() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return gpsAlertRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(GpsAlertResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GpsAlertResponse> getUnreadAlerts() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return gpsAlertRepository.findAllByTenantIdAndReadFalseOrderByCreatedAtDesc(tenantId).stream()
                .map(GpsAlertResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadAlertCount() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return gpsAlertRepository.countByTenantIdAndReadFalse(tenantId);
    }

    @Transactional
    public void markAlertAsRead(Long alertId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsAlert alert = gpsAlertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));
        if (!alert.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Alert not found: " + alertId);
        }
        alert.setRead(true);
        gpsAlertRepository.save(alert);
    }

    @Transactional
    public void markAllAlertsAsRead() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<GpsAlert> unread = gpsAlertRepository.findAllByTenantIdAndReadFalseOrderByCreatedAtDesc(tenantId);
        unread.forEach(a -> a.setRead(true));
        gpsAlertRepository.saveAll(unread);
    }

    // ── UPDATE: Vehicle GPS config ───────────────────────────────────────────

    @Transactional
    public GpsVehicleResponse updateVehicleGps(Long vehicleId, UpdateGpsRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        if (request.getGpsDeviceId() != null) {
            vehicle.setGpsDeviceId(request.getGpsDeviceId());
        }
        if (request.getGpsImei() != null) {
            vehicle.setGpsImei(request.getGpsImei());
        }
        if (request.getGpsEnabled() != null) {
            vehicle.setGpsEnabled(request.getGpsEnabled());
        }
        if (request.getGpsStatus() != null) {
            vehicle.setGpsStatus(request.getGpsStatus());
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

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Updated GPS config for vehicle [id={}] in tenant [{}]", vehicleId, tenantId);
        return GpsVehicleResponse.from(saved);
    }

    // ── CREATE: Alert ────────────────────────────────────────────────────────

    @Transactional
    public GpsAlertResponse createAlert(GpsAlertType type, String message, String severity,
                                         Long vehicleId, String vehicleName,
                                         Double latitude, Double longitude, Double speed) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .map(Vehicle::getTenant)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

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

        GpsAlert saved = gpsAlertRepository.save(alert);
        log.info("Created GPS alert [id={}] type={} for vehicle [id={}] in tenant [{}]",
                saved.getId(), type, vehicleId, tenantId);
        return GpsAlertResponse.from(saved);
    }
}
