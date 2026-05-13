package com.carrental.controller;

import com.carrental.dto.gps.*;
import com.carrental.service.GpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
}
