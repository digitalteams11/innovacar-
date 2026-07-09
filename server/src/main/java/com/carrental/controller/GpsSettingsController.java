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
    @PreAuthorize("@rolePermissionService.has('GPS_SETTINGS_VIEW')")
    public ResponseEntity<GpsSettingsResponse> getSettings() {
        return ResponseEntity.ok(gpsSettingsService.getSettings());
    }

    // ── POST /api/gps/settings ───────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('GPS_SETTINGS_UPDATE')")
    public ResponseEntity<GpsSettingsResponse> saveSettings(
            @Valid @RequestBody GpsSettingsRequest request) {
        return ResponseEntity.ok(gpsSettingsService.saveSettings(request));
    }

    // ── DELETE /api/gps/settings ─────────────────────────────────────────────
    // Full reset — removes the entire row (provider, base URL, credentials).

    @DeleteMapping
    @PreAuthorize("@rolePermissionService.has('GPS_CREDENTIALS_DELETE')")
    public ResponseEntity<Map<String, String>> deleteSettings() {
        gpsSettingsService.deleteSettings();
        return ResponseEntity.ok(Map.of("message", "GPS settings deleted successfully"));
    }

    // ── POST /api/gps/settings/deactivate ────────────────────────────────────
    // Disables tracking but keeps credentials, so reconnecting later doesn't
    // require re-entering the API key.

    @PostMapping("/deactivate")
    @PreAuthorize("@rolePermissionService.has('GPS_SETTINGS_UPDATE')")
    public ResponseEntity<Map<String, Object>> deactivate() {
        GpsSettingsResponse result = gpsSettingsService.deactivate();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "GPS integration deactivated successfully.",
                "data", result
        ));
    }

    // ── DELETE /api/gps/settings/credentials ─────────────────────────────────
    // Surgical reset — clears only the API key/APP ID, keeps provider/base URL.

    @DeleteMapping("/credentials")
    @PreAuthorize("@rolePermissionService.has('GPS_CREDENTIALS_DELETE')")
    public ResponseEntity<Map<String, Object>> deleteCredentials() {
        GpsSettingsResponse result = gpsSettingsService.deleteCredentials();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "GPS credentials deleted successfully.",
                "data", result
        ));
    }

    // ── POST /api/gps/settings/test ──────────────────────────────────────────

    @PostMapping("/test")
    @PreAuthorize("@rolePermissionService.has('GPS_TEST_CONNECTION')")
    public ResponseEntity<GpsConnectionTestResponse> testConnectionWithStoredSettings() {
        GpsSettings settings = gpsSettingsService.getSettingsEntity();
        GpsConnectionTestResponse result = gpsProviderService.testConnectionWithStoredSettings(settings);

        // Update connection status in DB based on the REAL test result — never
        // mark CONNECTED unless the provider actually confirmed it.
        if (result.isSuccess()) {
            gpsSettingsService.updateConnectionStatus("CONNECTED", null);
        } else {
            gpsSettingsService.updateConnectionStatus("FAILED", result.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    // ── POST /api/gps/settings/test-raw ──────────────────────────────────────

    @PostMapping("/test-raw")
    @PreAuthorize("@rolePermissionService.has('GPS_TEST_CONNECTION')")
    public ResponseEntity<GpsConnectionTestResponse> testConnectionWithRawCredentials(
            @Valid @RequestBody GpsConnectionTestRequest request) {
        GpsConnectionTestResponse result = gpsProviderService.testConnection(request);
        return ResponseEntity.ok(result);
    }

    // ── POST /api/gps/settings/sync ──────────────────────────────────────────

    @PostMapping("/sync")
    @PreAuthorize("@rolePermissionService.has('GPS_SYNC_DEVICES')")
    public ResponseEntity<GpsDeviceSyncResponse> syncDevices() {
        GpsSettings settings = gpsSettingsService.getSettingsEntity();
        if (settings == null || settings.getApiKeyEncrypted() == null || settings.getApiKeyEncrypted().isBlank()) {
            throw new IllegalStateException("Connect your GPS provider before syncing devices.");
        }

        GpsDeviceSyncResponse result = gpsProviderService.syncDevices(settings);

        if (result.isSuccess()) {
            gpsSettingsService.updateConnectionStatus("CONNECTED", null);
        } else {
            gpsSettingsService.updateConnectionStatus("FAILED", result.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
