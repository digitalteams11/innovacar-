package com.carrental.controller;

import com.carrental.dto.gps.*;
import com.carrental.entity.GpsSettings;
import com.carrental.service.GpsProviderService;
import com.carrental.service.GpsSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GPS Provider Configuration REST controller.
 *
 * <pre>
 * GET    /api/gps/settings          – get current tenant's GPS settings    [authenticated]
 * POST   /api/gps/settings          – save/update GPS settings             [ADMIN]
 * DELETE /api/gps/settings          – delete GPS settings                  [ADMIN]
 * POST   /api/gps/settings/test     – test connection with stored creds    [ADMIN]
 * POST   /api/gps/settings/test-raw – test connection with raw creds       [ADMIN]
 * POST   /api/gps/settings/sync     – sync devices from GPS provider       [ADMIN]
 * </pre>
 */
@RestController
@RequestMapping("/api/gps/settings")
@RequiredArgsConstructor
public class GpsSettingsController {

    private final GpsSettingsService gpsSettingsService;
    private final GpsProviderService gpsProviderService;

    // ── GET /api/gps/settings ────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<GpsSettingsResponse> getSettings() {
        return ResponseEntity.ok(gpsSettingsService.getSettings());
    }

    // ── POST /api/gps/settings ───────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpsSettingsResponse> saveSettings(
            @Valid @RequestBody GpsSettingsRequest request) {
        return ResponseEntity.ok(gpsSettingsService.saveSettings(request));
    }

    // ── DELETE /api/gps/settings ─────────────────────────────────────────────

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteSettings() {
        gpsSettingsService.deleteSettings();
        return ResponseEntity.ok(Map.of("message", "GPS settings deleted successfully"));
    }

    // ── POST /api/gps/settings/test ──────────────────────────────────────────

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpsConnectionTestResponse> testConnectionWithStoredSettings() {
        GpsSettings settings = gpsSettingsService.getSettingsEntity();
        GpsConnectionTestResponse result = gpsProviderService.testConnectionWithStoredSettings(settings);

        // Update connection status in DB based on test result
        if (result.isSuccess()) {
            gpsSettingsService.updateConnectionStatus("CONNECTED", null);
        } else {
            gpsSettingsService.updateConnectionStatus("ERROR", result.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    // ── POST /api/gps/settings/test-raw ──────────────────────────────────────

    @PostMapping("/test-raw")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpsConnectionTestResponse> testConnectionWithRawCredentials(
            @Valid @RequestBody GpsConnectionTestRequest request) {
        GpsConnectionTestResponse result = gpsProviderService.testConnection(request);
        return ResponseEntity.ok(result);
    }

    // ── POST /api/gps/settings/sync ──────────────────────────────────────────

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpsDeviceSyncResponse> syncDevices() {
        GpsSettings settings = gpsSettingsService.getSettingsEntity();
        if (settings == null) {
            return ResponseEntity.ok(GpsDeviceSyncResponse.builder()
                    .success(false)
                    .message("No GPS settings configured")
                    .build());
        }

        GpsDeviceSyncResponse result = gpsProviderService.syncDevices(settings);

        if (result.isSuccess()) {
            gpsSettingsService.updateConnectionStatus("CONNECTED", null);
        } else {
            gpsSettingsService.updateConnectionStatus("ERROR", result.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
