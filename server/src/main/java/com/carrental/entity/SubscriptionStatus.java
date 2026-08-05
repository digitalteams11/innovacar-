package com.carrental.entity;

/**
 * The full billing lifecycle for a tenant's subscription (9 values).
 *
 * <p>Deliberate exclusions: {@code BLOCKED}/{@code INACTIVE} are Super-Admin
 * manual account states, unrelated to the billing lifecycle — they are NOT
 * part of this enum and are tracked separately via {@link Tenant#getAccountState()}
 * (see {@link Tenant#isAccountBlocked()}). {@code PAST_DUE} was folded into
 * {@link #GRACE_PERIOD} (a payment failure now starts a real 3-day grace
 * window instead of jumping to a terminal state with no timer).
 * {@code CANCEL_SCHEDULED} was renamed {@link #CANCEL_AT_PERIOD_END} and
 * {@code EXPIRED} (trial-specific in practice) was renamed {@link #TRIAL_EXPIRED}.
 */
public enum SubscriptionStatus {
    /** Free trial window is currently active (see {@link Tenant#isInTrial()}). */
    TRIAL,
    /** Trial window has elapsed with no paid subscription activated. */
    TRIAL_EXPIRED,
    /** Checkout initiated but not yet confirmed by a Whop webhook. */
    CHECKOUT_PENDING,
    /** Paid subscription in good standing. */
    ACTIVE,
    /** Reserved for a future "renewal due soon, before any failure" advisory state. Not emitted by the current lifecycle. */
    PAYMENT_DUE,
    /** Payment failed; access remains fully usable until {@code gracePeriodEnd}. */
    GRACE_PERIOD,
    /** Grace period elapsed without a successful payment — writes are blocked. */
    SUSPENDED,
    /** User-requested cancellation; access continues until {@code cancelEffectiveAt}. */
    CANCEL_AT_PERIOD_END,
    /** Subscription is terminally cancelled. */
    CANCELLED
}
