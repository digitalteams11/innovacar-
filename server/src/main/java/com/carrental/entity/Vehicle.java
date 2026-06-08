package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single vehicle in a tenant's fleet with full pricing and status.
 */
@Entity
@Table(
    name = "vehicles",
    indexes = {
        @Index(name = "idx_vehicle_tenant", columnList = "tenant_id"),
        @Index(name = "idx_vehicle_tenant_statut", columnList = "tenant_id, statut"),
        @Index(name = "idx_vehicle_tenant_category", columnList = "tenant_id, category")
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

    /** Daily rental price */
    @Column(name = "prix_jour", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixJour;

    /** Weekly rental price (7+ days) */
    @Column(name = "prix_semaine", precision = 10, scale = 2)
    private BigDecimal prixSemaine;

    /** Monthly rental price (30+ days) */
    @Column(name = "prix_mois", precision = 10, scale = 2)
    private BigDecimal prixMois;

    /** Deposit amount required */
    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    /** Insurance fees per day */
    @Column(name = "insurance_fees", precision = 10, scale = 2)
    private BigDecimal insuranceFees;

    /** Delivery fees */
    @Column(name = "delivery_fees", precision = 10, scale = 2)
    private BigDecimal deliveryFees;

    /** Extra mileage cost per km */
    @Column(name = "extra_mileage_cost", precision = 10, scale = 2)
    private BigDecimal extraMileageCost;

    /** Current lifecycle state */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus statut;

    /** Vehicle category */
    @Column(length = 50)
    private String category;

    /** License plate */
    @Column(length = 30)
    private String plate;

    /** Fuel type */
    @Column(length = 20)
    private String fuel;

    /** Transmission */
    @Column(length = 20)
    private String transmission;

    /** Vehicle year */
    @Column
    private Integer year;

    /** Vehicle color */
    @Column(length = 30)
    private String color;

    /** Image URL */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** Insurance expiration date */
    @Column(name = "insurance_expiration")
    private LocalDate insuranceExpiration;

    /** Technical inspection expiration */
    @Column(name = "technical_inspection_expiration")
    private LocalDate technicalInspectionExpiration;

    // ── GPS Tracking fields ─────────────────────────────────────────────────

    @Column(name = "gps_device_id", length = 100)
    private String gpsDeviceId;

    @Column(name = "gps_imei", length = 50)
    private String gpsImei;

    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "last_gps_update")
    private LocalDateTime lastGpsUpdate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gps_status", length = 20)
    private GpsDeviceStatus gpsStatus;

    @Column(name = "last_speed")
    private Double lastSpeed;

    @Column(name = "gps_enabled")
    private Boolean gpsEnabled = false;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
