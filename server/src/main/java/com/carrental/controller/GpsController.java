package com.carrental.controller;

import com.carrental.dto.gps.*;
import com.carrental.entity.GpsDevice;
import com.carrental.entity.GpsSettings;
import com.carrental.entity.Vehicle;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.GpsDeviceRepository;
import com.carrental.repository.GpsSettingsRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.GpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GPS Tracking REST controller.
 *
 * <pre>
 * GET /api/gps/vehicles          – all vehicles with GPS data        [authenticated]
 * GET /api/gps/tracked           – only GPS-enabled vehicles         [authenticated]
 * GET /api/gps/vehicles/{id}     – single vehicle position           [authenticated]
 * GET /api/gps/stats             – dashboard statistics              [authenticated]
 * GET /api/gps/history/{id}      – vehicle route history             [authenticated]
 * GET /api/gps/trips/{id}        – vehicle trip segments             [authenticated]
 * GET /api/gps/alerts            – all alerts                       [authenticated]
 * GET /api/gps/alerts/unread     – unread alerts                    [authenticated]
 * GET /api/gps/alerts/count      – unread alert count               [authenticated]
 * PATCH /api/gps/alerts/{id}/read – mark alert as read              [authenticated]
 * PATCH /api/gps/alerts/read-all – mark all alerts as read          [authenticated]
 * PUT /api/gps/vehicles/{id}     – update vehicle GPS config         [ADMIN]
 * </pre>
 */
@RestController
@RequestMapping("/api/gps")
@RequiredArgsConstructor
public class GpsController {

    private final GpsService gpsService;
    private final GpsSettingsRepository gpsSettingsRepository;
    private final GpsDeviceRepository gpsDeviceRepository;
    private final VehicleRepository vehicleRepository;

    // ── GET /api/gps/status ──────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsSettings settings = tenantId != null
                ? gpsSettingsRepository.findByTenantId(tenantId).orElse(null)
                : null;

        Map<String, Object> data = new LinkedHashMap<>();
        if (settings == null || !Boolean.TRUE.equals(settings.getEnabled())) {
            data.put("configured", false);
            data.put("provider", null);
            data.put("connected", false);
            data.put("deviceCount", 0);
            data.put("lastSyncAt", null);
            data.put("message", "GPS is not configured yet");
        } else {
            boolean connected = "CONNECTED".equalsIgnoreCase(settings.getConnectionStatus());
            data.put("configured", true);
            data.put("provider", settings.getProvider());
            data.put("connected", connected);
            data.put("deviceCount", settings.getActiveDevices() != null ? settings.getActiveDevices() : 0);
            data.put("lastSyncAt", settings.getLastSyncAt());
            data.put("message", connected ? "GPS connected" : "GPS not connected");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    // ── GET /api/gps/vehicles ────────────────────────────────────────────────

    @GetMapping("/vehicles")
    public ResponseEntity<List<GpsVehicleResponse>> getAllVehiclesWithGps() {
        return ResponseEntity.ok(gpsService.getLivePositions());
    }

    // ── GET /api/gps/tracked ─────────────────────────────────────────────────

    @GetMapping("/tracked")
    public ResponseEntity<List<GpsVehicleResponse>> getTrackedVehicles() {
        return ResponseEntity.ok(gpsService.getTrackedVehicles());
    }

    // ── GET /api/gps/vehicles/{id} ───────────────────────────────────────────

    @GetMapping("/vehicles/{id}")
    public ResponseEntity<GpsVehicleResponse> getVehiclePosition(@PathVariable Long id) {
        return ResponseEntity.ok(gpsService.getVehiclePosition(id));
    }

    // ── GET /api/gps/stats ───────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<GpsDashboardStats> getStats() {
        return ResponseEntity.ok(gpsService.getDashboardStats());
    }

    // ── GET /api/gps/history/{id} ────────────────────────────────────────────

    @GetMapping("/history/{id}")
    public ResponseEntity<List<GpsHistoryResponse>> getHistory(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(gpsService.getVehicleHistory(id, from, to));
    }

    // ── GET /api/gps/trips/{id} ──────────────────────────────────────────────

    @GetMapping("/trips/{id}")
    public ResponseEntity<List<GpsTripResponse>> getTrips(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(gpsService.getTrips(id, from, to));
    }

    // ── GET /api/gps/alerts ──────────────────────────────────────────────────

    @GetMapping("/alerts")
    public ResponseEntity<List<GpsAlertResponse>> getAlerts() {
        return ResponseEntity.ok(gpsService.getAlerts());
    }

    // ── GET /api/gps/alerts/unread ───────────────────────────────────────────

    @GetMapping("/alerts/unread")
    public ResponseEntity<List<GpsAlertResponse>> getUnreadAlerts() {
        return ResponseEntity.ok(gpsService.getUnreadAlerts());
    }

    // ── GET /api/gps/alerts/count ────────────────────────────────────────────

    @GetMapping("/alerts/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", gpsService.getUnreadAlertCount()));
    }

    // ── PATCH /api/gps/alerts/{id}/read ──────────────────────────────────────

    @PatchMapping("/alerts/{id}/read")
    public ResponseEntity<Map<String, String>> markAlertRead(@PathVariable Long id) {
        gpsService.markAlertAsRead(id);
        return ResponseEntity.ok(Map.of("message", "Alert marked as read"));
    }

    // ── PATCH /api/gps/alerts/read-all ───────────────────────────────────────

    @PatchMapping("/alerts/read-all")
    public ResponseEntity<Map<String, String>> markAllAlertsRead() {
        gpsService.markAllAlertsAsRead();
        return ResponseEntity.ok(Map.of("message", "All alerts marked as read"));
    }

    // ── PUT /api/gps/vehicles/{id} ───────────────────────────────────────────

    @PutMapping("/vehicles/{id}")
    public ResponseEntity<GpsVehicleResponse> updateVehicleGps(
            @PathVariable Long id,
            @RequestBody UpdateGpsRequest request) {
        return ResponseEntity.ok(gpsService.updateVehicleGps(id, request));
    }

    // ── GET /api/gps/devices ─────────────────────────────────────────────────

    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> getDevices() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<GpsDevice> devices = gpsDeviceRepository.findAllByTenantId(tenantId);

        long online  = devices.stream().filter(d -> "ONLINE".equals(d.getStatus())).count();
        long offline = devices.stream().filter(d -> "OFFLINE".equals(d.getStatus())).count();
        long moving  = devices.stream().filter(d -> "MOVING".equals(d.getStatus())).count();
        long stopped = devices.stream().filter(d -> "STOPPED".equals(d.getStatus())).count();

        List<Map<String, Object>> deviceList = devices.stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("providerDeviceId", d.getProviderDeviceId());
            item.put("name", d.getName());
            item.put("imei", d.getImei());
            item.put("plateNumber", d.getPlateNumber());
            item.put("status", d.getStatus());
            item.put("latitude", d.getLatitude());
            item.put("longitude", d.getLongitude());
            item.put("speed", d.getSpeed());
            item.put("ignition", d.getIgnition());
            item.put("vehicleId", d.getVehicleId());
            item.put("lastSyncedAt", d.getLastSyncedAt());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", devices.size());
        data.put("online", online);
        data.put("offline", offline);
        data.put("moving", moving);
        data.put("stopped", stopped);
        data.put("devices", deviceList);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/gps/devices/{id}/link-vehicle ───────────────────────────────

    @Transactional
    @PostMapping("/devices/{id}/link-vehicle")
    public ResponseEntity<Map<String, Object>> linkVehicle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsDevice device = gpsDeviceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GPS device not found: " + id));

        Object vehicleIdRaw = body.get("vehicleId");
        Long vehicleId = vehicleIdRaw != null ? Long.parseLong(vehicleIdRaw.toString()) : null;

        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
            device.setVehicleId(vehicleId);
            vehicle.setGpsEnabled(true);
            if (device.getImei() != null && !device.getImei().isBlank()) {
                vehicle.setGpsImei(device.getImei());
            }
            vehicleRepository.save(vehicle);
        } else {
            device.setVehicleId(null);
        }

        gpsDeviceRepository.save(device);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", vehicleId != null ? "Device linked to vehicle" : "Device unlinked");
        response.put("deviceId", device.getId());
        response.put("vehicleId", device.getVehicleId());
        return ResponseEntity.ok(response);
    }
}
