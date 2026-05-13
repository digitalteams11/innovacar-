package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single vehicle in a tenant's fleet.
 *
 * <p>Multi-tenancy: every vehicle is bound to exactly one {@link Tenant}.
 * The {@code tenant_id} column is the discriminator used by all repository
 * queries to enforce row-level tenant isolation.
 */
@Entity
@Table(
    name = "vehicles",
    indexes = {
        @Index(name = "idx_vehicle_tenant",        columnList = "tenant_id"),
        @Index(name = "idx_vehicle_tenant_statut",  columnList = "tenant_id, statut")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Brand and/or model name, e.g. "Toyota Corolla 2023" */
    @Column(nullable = false, length = 150)
    private String marque;

    /** Daily rental price (stored as NUMERIC(10,2) in PostgreSQL) */
    @Column(name = "prix_jour", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixJour;

    /** Current lifecycle state of the vehicle */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus statut;

    /** Vehicle category e.g. Economy, SUV, Luxury */
    @Column(length = 50)
    private String category;

    /** License plate number */
    @Column(length = 30)
    private String plate;

    /** Fuel type: Essence, Diesel, Hybrid, Electric */
    @Column(length = 20)
    private String fuel;

    /** Transmission: Manual, Automatic */
    @Column(length = 20)
    private String transmission;

    /** Image URL for the vehicle (Base64 data URL or external URL) */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    // ── GPS Tracking fields ─────────────────────────────────────────────────

    /** GPS device identifier from the tracking provider (e.g. IOPGPS) */
    @Column(name = "gps_device_id", length = 100)
    private String gpsDeviceId;

    /** Device IMEI number */
    @Column(name = "gps_imei", length = 50)
    private String gpsImei;

    /** Last known latitude */
    @Column(name = "last_latitude")
    private Double lastLatitude;

    /** Last known longitude */
    @Column(name = "last_longitude")
    private Double lastLongitude;

    /** Last GPS update timestamp */
    @Column(name = "last_gps_update")
    private LocalDateTime lastGpsUpdate;

    /** Current GPS device status */
    @Enumerated(EnumType.STRING)
    @Column(name = "gps_status", length = 20)
    private GpsDeviceStatus gpsStatus;

    /** Last reported speed in km/h */
    @Column(name = "last_speed")
    private Double lastSpeed;

    /** GPS tracking enabled for this vehicle */
    @Column(name = "gps_enabled")
    private Boolean gpsEnabled = false;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
