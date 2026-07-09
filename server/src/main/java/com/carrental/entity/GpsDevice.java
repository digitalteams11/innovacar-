package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A GPS device synced from an external provider.
 * May optionally be linked to a vehicle.
 */
@Entity
@Table(
    name = "gps_devices",
    indexes = {
        @Index(name = "idx_gps_devices_tenant",          columnList = "tenant_id"),
        @Index(name = "idx_gps_devices_tenant_provider", columnList = "tenant_id, provider_device_id", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Nullable soft-link to a Vehicle record. */
    @Column(name = "vehicle_id")
    private Long vehicleId;

    /** Device identifier assigned by the GPS provider. */
    @Column(name = "provider_device_id", nullable = false, length = 200)
    private String providerDeviceId;

    @Column(name = "imei", length = 100)
    private String imei;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "plate_number", length = 50)
    private String plateNumber;

    /** ONLINE / OFFLINE / MOVING / STOPPED / IDLE / UNKNOWN */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "UNKNOWN";

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "speed")
    private Double speed;

    @Column(name = "ignition")
    @Builder.Default
    private Boolean ignition = false;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt    = now;
        updatedAt    = now;
        lastSyncedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
