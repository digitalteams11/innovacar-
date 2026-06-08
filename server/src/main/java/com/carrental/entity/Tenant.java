package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a tenant (company / business) in the SaaS platform.
 * Every user and every piece of data belongs to exactly one tenant.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Company / business name */
    @Column(nullable = false, unique = true)
    private String name;

    /** Billing / contact e-mail — also unique per tenant */
    @Column(nullable = false, unique = true)
    private String email;

    /** Whether the subscription is currently active */
    @Column(nullable = false)
    private boolean subscriptionActive;

    /** Date on which the subscription expires */
    @Column
    private LocalDate subscriptionEndDate;

    /** Agency address */
    @Column
    private String address;

    /** Agency phone */
    @Column
    private String phone;

    /** Tax ID */
    @Column(name = "tax_id")
    private String taxId;

    /** City */
    @Column
    private String city;

    /** Country */
    @Column
    private String country;

    /** Logo URL (can be a URL or base64 data URL) */
    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    /** Agency owner signature (base64 PNG) — used for all contracts */
    @Column(name = "agency_signature", columnDefinition = "TEXT")
    private String agencySignature;

    /** Agency decorative stamp/image (base64 PNG) */
    @Column(name = "agency_stamp_url", columnDefinition = "TEXT")
    private String agencyStampUrl;

    /** Agency-specific terms & conditions for contracts */
    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    /** Current subscription plan name */
    @Column
    private String planName;

    /** Account status: ACTIVE, SUSPENDED, TRIAL, EXPIRED */
    @Column
    private String status;

    /** Max vehicles allowed */
    @Column(name = "max_vehicles")
    private Integer maxVehicles;

    /** Max employees allowed */
    @Column(name = "max_employees")
    private Integer maxEmployees;

    /** Max GPS devices allowed */
    @Column(name = "max_gps_devices")
    private Integer maxGpsDevices;

    /** Max reservations allowed per month */
    @Column(name = "max_reservations")
    private Integer maxReservations;

    /** Storage limit in MB */
    @Column(name = "storage_limit_mb")
    private Integer storageLimitMb;

    /** Trial start date */
    @Column(name = "trial_start_date")
    private LocalDate trialStartDate;

    /** Trial end date (default 2 months) */
    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    /** When the tenant was created */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Last updated timestamp */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "TRIAL";
        if (planName == null) planName = "Trial";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if the subscription is currently active and not expired.
     */
    public boolean isSubscriptionValid() {
        if (!subscriptionActive) return false;
        if (subscriptionEndDate != null && LocalDate.now().isAfter(subscriptionEndDate)) return false;
        return true;
    }

    /**
     * Checks if the tenant is currently in trial period.
     */
    public boolean isInTrial() {
        if (trialEndDate == null) return false;
        return LocalDate.now().isBefore(trialEndDate) || LocalDate.now().isEqual(trialEndDate);
    }
}
