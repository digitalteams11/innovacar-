package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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

    /** Every new agency gets exactly one calendar month of free trial — not 60 days, not 2 months. */
    public static final int TRIAL_PERIOD_MONTHS = 1;

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

    /**
     * Billing lifecycle state — see {@link SubscriptionStatus} for the 9
     * possible values. Deliberately does NOT include BLOCKED/INACTIVE (see
     * {@link #accountState}) — those are Super-Admin manual actions, not
     * billing-lifecycle transitions.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private SubscriptionStatus status;

    /**
     * Super-Admin manual account state (BLOCKED / INACTIVE), orthogonal to
     * the billing lifecycle in {@link #status}. Null means "no manual
     * override" — the account is governed purely by its billing status.
     * {@code SUSPENDED} is deliberately NOT stored here: it is a real
     * billing-lifecycle terminal state (both a Super-Admin manual suspend
     * and the automated post-grace-period suspension land on
     * {@code status = SUSPENDED}), so it stays in {@link #status}.
     */
    @Column(name = "account_state")
    private String accountState;

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

    /** Trial start date — the agency/account creation date. Legacy, date-only; see trialStartedAt for the precise timestamp. */
    @Column(name = "trial_start_date")
    private LocalDate trialStartDate;

    /** Trial end date — legacy, date-only; see trialEndsAt for the precise timestamp that actually governs expiry. */
    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    /**
     * Precise trial start instant. A day-count trial (trialDays = 1, 7, 14...)
     * cannot be correctly enforced with date-only columns — comparing against
     * a plain LocalDate only flips to expired at the next midnight, silently
     * granting up to one extra day regardless of the exact signup time. This
     * (and trialEndsAt) is the real source of truth for isInTrial/isTrialExpired;
     * trialStartDate/trialEndDate are kept in sync for legacy/display readers
     * but never consulted for the actual expiry decision once this is set.
     */
    @Column(name = "trial_started_at")
    private LocalDateTime trialStartedAt;

    /** Precise trial end instant — {@code trialStartedAt + plan.trialDays}. See {@link #trialStartedAt}. */
    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    /** Dedup marker: trial-ends-in-7-days reminder already sent (null = not yet sent). */
    @Column(name = "trial_reminder_7_sent_at")
    private LocalDateTime trialReminder7SentAt;

    /** Dedup marker: trial-ends-in-3-days reminder already sent (null = not yet sent). */
    @Column(name = "trial_reminder_3_sent_at")
    private LocalDateTime trialReminder3SentAt;

    /** Dedup marker: trial-ends-in-1-day reminder already sent (null = not yet sent). */
    @Column(name = "trial_reminder_1_sent_at")
    private LocalDateTime trialReminder1SentAt;

    /** Dedup marker: trial-expired notification already sent (null = not yet sent). */
    @Column(name = "trial_expired_notified_at")
    private LocalDateTime trialExpiredNotifiedAt;

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

    /** Precise current paid-period start instant (new source of truth; {@link #trialStartDate}-style legacy columns stay date-only). */
    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    /** Precise current paid-period end instant. {@link #subscriptionEndDate} is kept in sync as a legacy-compat date-only mirror. */
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    /** Deadline for the current grace period — after this instant, the scheduler suspends the tenant. Null when not in GRACE_PERIOD. */
    @Column(name = "grace_period_end")
    private LocalDateTime gracePeriodEnd;

    /** When the tenant was actually suspended (grace period elapsed, or a manual Super-Admin suspend). */
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /** Dedup marker: "grace period started" notice already sent for the current grace window. */
    @Column(name = "grace_started_notified_at")
    private LocalDateTime graceStartedNotifiedAt;

    /** Dedup marker: "suspended tomorrow" (grace ends within 24h) warning already sent for the current grace window. */
    @Column(name = "grace_suspension_warning_notified_at")
    private LocalDateTime graceSuspensionWarningNotifiedAt;

    /** Dedup marker: renewal-in-5-days reminder already sent for the current period. */
    @Column(name = "renewal_reminder_5_sent_at")
    private LocalDateTime renewalReminder5SentAt;

    /** Dedup marker: renewal-in-3-days reminder already sent for the current period. */
    @Column(name = "renewal_reminder_3_sent_at")
    private LocalDateTime renewalReminder3SentAt;

    /** Dedup marker: renewal-in-1-day reminder already sent for the current period. */
    @Column(name = "renewal_reminder_1_sent_at")
    private LocalDateTime renewalReminder1SentAt;

    /**
     * Display-only IANA timezone (e.g. "Africa/Casablanca") used to format
     * timestamps in the UI/emails. Never used for backend arithmetic — grace/
     * trial/period deadlines are always plain {@link LocalDateTime} deltas on
     * the server's own clock, exactly as {@link #isTrialExpired()} already did
     * before this field existed.
     */
    @Column(name = "timezone")
    private String timezone;

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
        if (status == null) status = SubscriptionStatus.TRIAL;
        if (planName == null) planName = "Trial";
        if (verificationStatus == null) verificationStatus = "PENDING_VERIFICATION";
        if (balance == null) balance = java.math.BigDecimal.ZERO;
        if (timezone == null) timezone = "Africa/Casablanca";
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
     *
     * <p>BLOCKED/INACTIVE are manual Super-Admin states tracked on
     * {@link #accountState} (not part of {@link SubscriptionStatus} — see that
     * enum's Javadoc). SUSPENDED is a real billing-lifecycle terminal state
     * (manual suspend and automated post-grace-period suspension both land on
     * {@code status == SUSPENDED}), so it's checked on {@link #status} instead.
     */
    public boolean isAccountBlocked() {
        if (accountState != null
                && ("BLOCKED".equalsIgnoreCase(accountState) || "INACTIVE".equalsIgnoreCase(accountState))) {
            return true;
        }
        return status == SubscriptionStatus.SUSPENDED;
    }

    /**
     * Checks if the subscription is currently active and not expired.
     * A deliberate Super-Admin block/suspend/deactivate always wins; a live
     * free-access grant overrides normal plan/payment state otherwise.
     *
     * <p>A tenant whose status is still "TRIAL" is validated against the exact
     * trialEndsAt instant (via isInTrial()), never against subscriptionActive/
     * subscriptionEndDate — those two fields describe a *paid* subscription
     * window and are deliberately left true/null for a brand-new trial tenant
     * (see AuthService#createTrialTenant), so checking them here previously
     * meant a trial tenant was reported "valid" forever, even hours/days after
     * its precise trialEndsAt had passed and regardless of whether the nightly
     * repair job had gotten around to flipping status to "EXPIRED" yet. This
     * is the real-time check the product requires independent of that job
     * (see SubscriptionService#repairSubscriptionState).
     */
    public boolean isSubscriptionValid() {
        if (isAccountBlocked()) return false;
        if (hasActiveFreeAccess()) return true;
        // CANCEL_AT_PERIOD_END: paid access continues until cancelEffectiveAt
        if (isCancelScheduled() && cancelEffectiveAt != null && LocalDateTime.now().isBefore(cancelEffectiveAt)) return true;
        // GRACE_PERIOD is deliberately fully usable — grace is NOT blocked (per spec).
        if (status == SubscriptionStatus.GRACE_PERIOD) return true;
        if (status == SubscriptionStatus.TRIAL) return isInTrial();
        if (!subscriptionActive) return false;
        if (subscriptionEndDate != null && LocalDate.now().isAfter(subscriptionEndDate)) return false;
        return true;
    }

    /**
     * Checks if the tenant is currently in trial period. Prefers the precise
     * trialEndsAt timestamp (exact-instant expiry); falls back to the legacy
     * date-only trialEndDate for tenants created before trialEndsAt existed.
     */
    public boolean isInTrial() {
        if (trialEndsAt != null) return LocalDateTime.now().isBefore(trialEndsAt);
        if (trialEndDate == null) return false;
        return !LocalDate.now().isAfter(trialEndDate);
    }

    /** Whole calendar days left in the trial, floored at 0 — never negative, never null-unsafe. */
    public long trialDaysRemaining() {
        if (trialEndsAt != null) {
            Duration remaining = Duration.between(LocalDateTime.now(), trialEndsAt);
            return remaining.isNegative() ? 0 : remaining.toDays();
        }
        if (trialEndDate == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), trialEndDate));
    }

    /**
     * Exact remaining trial duration, never negative — the source for an
     * hours/minutes-accurate countdown display (a whole-day count alone can't
     * distinguish "18 hours left" from "13 days left" the way the product
     * requires). Zero once the trial has expired, never negative.
     */
    public Duration trialTimeRemaining() {
        LocalDateTime end = trialEndsAt != null ? trialEndsAt : (trialEndDate != null ? trialEndDate.atTime(23, 59, 59) : null);
        if (end == null) return Duration.ZERO;
        Duration remaining = Duration.between(LocalDateTime.now(), end);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * True once the trial has passed. Prefers the precise trialEndsAt instant
     * (>= comparison, per "now >= trialEndsAt is expired"); falls back to the
     * legacy date-only trialEndDate (day-granularity: the end date itself
     * still counts as active) for tenants without a precise timestamp.
     */
    public boolean isTrialExpired() {
        if (trialEndsAt != null) return !LocalDateTime.now().isBefore(trialEndsAt);
        return trialEndDate != null && LocalDate.now().isAfter(trialEndDate);
    }

    /**
     * True when a scheduled cancellation is pending but has not yet taken effect.
     * The subscription remains active and usable until {@link #cancelEffectiveAt}.
     */
    public boolean isCancelScheduled() {
        return status == SubscriptionStatus.CANCEL_AT_PERIOD_END;
    }
}
