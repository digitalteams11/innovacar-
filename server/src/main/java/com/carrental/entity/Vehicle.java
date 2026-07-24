package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single vehicle in a tenant's fleet with full pricing and status.
 */
@Entity
@SQLRestriction("coalesce(deleted, false) = false")
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

    /** Split-out brand, e.g. "Toyota" — backfilled from marque, used by fleet exports. */
    @Column(length = 100)
    private String brand;

    /** Split-out model, e.g. "Corolla 2023" — backfilled from marque, used by fleet exports. */
    @Column(length = 100)
    private String model;

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
    @Column(name = "\"year\"")
    private Integer year;

    /** Vehicle color */
    @Column(length = 30)
    private String color;

    /** Number of seats — entered manually by the agency, never inferred/defaulted. Null until an admin sets it. */
    @Column(name = "seat_count")
    private Integer seatCount;

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

    @Column(name = "out_of_zone")
    private Boolean outOfZone = false;

    @Column(name = "last_speed")
    private Double lastSpeed;

    @Column(name = "gps_enabled")
    private Boolean gpsEnabled = false;

    // ── Vehicle Condition & Tracking ────────────────────────────────────────

    @Column(name = "fuel_level_current", length = 30)
    private String fuelLevelCurrent;

    @Column(name = "mileage_current")
    private Integer mileageCurrent;

    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;

    @Column(name = "circulation_authorization_expiry_date")
    private LocalDate circulationAuthorizationExpiryDate;

    /** GOOD / WARNING / NEEDS_SERVICE */
    @Column(name = "condition_status", length = 30)
    private String conditionStatus;

    @Column(name = "last_inspection_at")
    private LocalDateTime lastInspectionAt;

    @Column(name = "last_returned_at")
    private LocalDateTime lastReturnedAt;

    @Builder.Default
    @Column(name = "deleted", columnDefinition = "boolean default false")
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 150)
    private String deletedBy;

    // Captures the statut held right before this vehicle was moved to trash,
    // so restore can put it back (e.g. IN_MAINTENANCE) instead of always
    // forcing AVAILABLE. Cleared again on restore.
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_delete", length = 20)
    private VehicleStatus statusBeforeDelete;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @PrePersist
    protected void onCreate() {
        if (deleted == null) deleted = false;
    }
}
