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

    /** Verification lifecycle is separate from subscription/account status. */
    @Column(name = "verification_status")
    private String verificationStatus;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    /** Super-Admin-negotiated custom monthly price overriding the plan's list price (nullable). */
    @Column(name = "custom_monthly_price", precision = 10, scale = 2)
    private java.math.BigDecimal customMonthlyPrice;

    /** Reason/context for the custom price override. */
    @Column(name = "custom_price_note", length = 500)
    private String customPriceNote;

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

    /** Current account balance/credit, in the platform's billing currency. Never mutated directly — only via AgencyBalanceTransaction. */
    @Column(name = "balance", precision = 12, scale = 2)
    private java.math.BigDecimal balance;

    /** Super-Admin-granted free access expiry (nullable). While in the future, the agency is treated as fully subscribed regardless of plan/payment state. */
    @Column(name = "free_access_until")
    private LocalDate freeAccessUntil;

    /** Reason/context for the free-access grant, shown to the agency as "Special access by Innovax Technologies". */
    @Column(name = "free_access_reason", length = 500)
    private String freeAccessReason;

    /** When the agency requested scheduled cancellation. Null if no cancellation is pending. */
    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    /** End-of-period datetime when CANCEL_SCHEDULED → CANCELLED transition will happen. */
    @Column(name = "cancel_effective_at")
    private LocalDateTime cancelEffectiveAt;

    /** Agency-provided cancellation reason code (e.g. TOO_EXPENSIVE). */
    @Column(name = "cancellation_reason", length = 100)
    private String cancellationReason;

    /** Optional free-text feedback provided at cancellation time. */
    @Column(name = "cancellation_feedback", columnDefinition = "TEXT")
    private String cancellationFeedback;

    /** When the subscription was actually transitioned to CANCELLED by the lifecycle job. */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

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
        if (verificationStatus == null) verificationStatus = "PENDING_VERIFICATION";
        if (balance == null) balance = java.math.BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** True while a Super-Admin-granted free access window is currently active. */
    public boolean hasActiveFreeAccess() {
        return freeAccessUntil != null && !LocalDate.now().isAfter(freeAccessUntil);
    }

    /**
     * True when a Super Admin has deliberately blocked/suspended/deactivated this
     * agency's account (as opposed to a subscription merely lapsing on its own).
     * This must out-rank everything else — even an active free-access override or
     * a paid plan — per the access-priority rules: blocked/suspended/inactive
     * agencies are blocked first, before plan/subscription state is even considered.
     */
    public boolean isAccountBlocked() {
        return status != null && (status.equalsIgnoreCase("BLOCKED")
                || status.equalsIgnoreCase("SUSPENDED")
                || status.equalsIgnoreCase("INACTIVE"));
    }

    /**
     * Checks if the subscription is currently active and not expired.
     * A deliberate Super-Admin block/suspend/deactivate always wins; a live
     * free-access grant overrides normal plan/payment state otherwise.
     */
    public boolean isSubscriptionValid() {
        if (isAccountBlocked()) return false;
        if (hasActiveFreeAccess()) return true;
        // CANCEL_SCHEDULED: paid access continues until cancelEffectiveAt
        if (isCancelScheduled() && cancelEffectiveAt != null && LocalDateTime.now().isBefore(cancelEffectiveAt)) return true;
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

    /**
     * True when a scheduled cancellation is pending but has not yet taken effect.
     * The subscription remains active and usable until {@link #cancelEffectiveAt}.
     */
    public boolean isCancelScheduled() {
        return "CANCEL_SCHEDULED".equalsIgnoreCase(status);
    }
}
