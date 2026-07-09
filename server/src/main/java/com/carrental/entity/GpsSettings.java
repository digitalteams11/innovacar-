package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * GPS provider configuration per tenant.
 * Stores API credentials encrypted at rest.
 */
@Entity
@Table(
    name = "gps_settings",
    indexes = {
        @Index(name = "idx_gps_settings_tenant", columnList = "tenant_id", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GPS provider name: IOPGPS, TRACCAR, WIALON, GPSWOX, CUSTOM */
    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    /** Application ID / Account ID */
    @Column(name = "app_id", length = 200)
    private String appId;

    /** API key — encrypted at rest */
    @Column(name = "api_key_encrypted", length = 500)
    private String apiKeyEncrypted;

    /** Base URL of the GPS provider API */
    @Column(name = "base_url", length = 300)
    private String baseUrl;

    /** Device group ID (optional) */
    @Column(name = "device_group_id", length = 100)
    private String deviceGroupId;

    /** Webhook URL for provider callbacks (optional) */
    @Column(name = "webhook_url", length = 300)
    private String webhookUrl;

    /** Connection status: CONNECTED, DISCONNECTED, ERROR, PENDING */
    @Column(name = "connection_status", length = 20)
    private String connectionStatus;

    /** Last successful sync timestamp */
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    /** Last time a connection test was attempted (success or failure) */
    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    /** Number of active GPS devices synced */
    @Column(name = "active_devices")
    private Integer activeDevices;

    /** Error message from last connection attempt */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** Encrypted password — used for Traccar Basic Auth */
    @Column(name = "encrypted_password", length = 500)
    private String encryptedPassword;

    /** Auth header name for Custom API providers (default: Authorization) */
    @Column(name = "auth_header_name", length = 100)
    private String authHeaderName;

    /** Auth prefix for Custom API providers (default: Bearer) */
    @Column(name = "auth_prefix", length = 50)
    private String authPrefix;

    /** Whether GPS tracking is enabled for this tenant */
    @Column(name = "enabled")
    private Boolean enabled = false;

    // ── Geofence & alert configuration ─────────────────────────────────────

    /** City center latitude for geofence calculations */
    @Column(name = "city_lat")
    private Double cityLat;

    /** City center longitude for geofence calculations */
    @Column(name = "city_lng")
    private Double cityLng;

    /** Allowed radius from city center in kilometres (default 50) */
    @Column(name = "radius_km")
    private Double radiusKm;

    /** Minimum movement in metres to count as "started moving" (default 50) */
    @Column(name = "movement_threshold_m")
    private Integer movementThresholdM;

    /** Minutes without GPS update before device is considered offline (default 30) */
    @Column(name = "inactivity_timeout_min")
    private Integer inactivityTimeoutMin;

    /** Trigger alert when a vehicle starts moving */
    @Column(name = "notify_movement")
    private Boolean notifyMovement;

    /** Trigger alert when a vehicle exits / re-enters the allowed city zone */
    @Column(name = "notify_geofence")
    private Boolean notifyGeofence;

    /** Trigger alert when a device goes offline */
    @Column(name = "notify_offline")
    private Boolean notifyOffline;

    /** How often (seconds) the background scheduler polls for state changes */
    @Column(name = "polling_interval_sec")
    private Integer pollingIntervalSec;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (connectionStatus == null) {
            connectionStatus = "DISCONNECTED";
        }
        if (enabled == null) {
            enabled = false;
        }
        if (activeDevices == null) {
            activeDevices = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
