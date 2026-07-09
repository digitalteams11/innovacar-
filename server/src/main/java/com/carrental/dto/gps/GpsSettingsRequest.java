package com.carrental.dto.gps;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to save or update GPS provider configuration.
 */
@Data
public class GpsSettingsRequest {

    @NotBlank(message = "Provider is required")
    @Pattern(regexp = "^(IOPGPS|TRACCAR|WIALON|GPSWOX|CUSTOM)$", message = "Invalid provider")
    @Size(max = 30)
    private String provider;

    @Size(max = 200)
    private String appId;

    @Size(max = 500)
    private String apiKey;

    /** For Traccar: basic-auth password (encrypted before storage, never returned). */
    @Size(max = 500)
    private String password;

    @Size(max = 300)
    private String baseUrl;

    /** For Custom API: header name (default: Authorization). */
    @Size(max = 100)
    private String authHeaderName;

    /** For Custom API: auth prefix (default: Bearer). */
    @Size(max = 50)
    private String authPrefix;

    @Size(max = 100)
    private String deviceGroupId;

    @Size(max = 300)
    private String webhookUrl;

    private Boolean enabled;

    // ── Geofence & alert configuration ─────────────────────────────────────

    private Double cityLat;
    private Double cityLng;
    private Double radiusKm;
    private Integer movementThresholdM;
    private Integer inactivityTimeoutMin;
    private Boolean notifyMovement;
    private Boolean notifyGeofence;
    private Boolean notifyOffline;
    private Integer pollingIntervalSec;
}
