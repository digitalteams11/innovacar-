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

    /** Number of active GPS devices synced */
    @Column(name = "active_devices")
    private Integer activeDevices;

    /** Error message from last connection attempt */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** Whether GPS tracking is enabled for this tenant */
    @Column(name = "enabled")
    private Boolean enabled = false;

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
