-- Subscription simplification: real 9-value status enum + grace period + one paid plan.

ALTER TABLE tenants
    ADD COLUMN current_period_start TIMESTAMP,
    ADD COLUMN current_period_end TIMESTAMP,
    ADD COLUMN grace_period_end TIMESTAMP,
    ADD COLUMN suspended_at TIMESTAMP,
    ADD COLUMN grace_started_notified_at TIMESTAMP,
    ADD COLUMN grace_suspension_warning_notified_at TIMESTAMP,
    ADD COLUMN renewal_reminder_5_sent_at TIMESTAMP,
    ADD COLUMN renewal_reminder_3_sent_at TIMESTAMP,
    ADD COLUMN renewal_reminder_1_sent_at TIMESTAMP,
    ADD COLUMN timezone VARCHAR(50) NOT NULL DEFAULT 'Africa/Casablanca',
    ADD COLUMN account_state VARCHAR(20);

ALTER TABLE subscription_plans
    ADD COLUMN grace_period_days INTEGER NOT NULL DEFAULT 3;

-- Backfill current_period_end from the legacy date-only column for existing paid tenants.
UPDATE tenants SET current_period_end = subscription_end_date::timestamp
    WHERE subscription_end_date IS NOT NULL AND current_period_end IS NULL;

-- Consolidate the paid catalog onto one row (reuses the existing "premium" row's config).
-- Seed codes/names are lower/mixed-case (see DataInitializer) — match case-insensitively.
UPDATE subscription_plans SET code = 'COMPLETE', name = 'Innovacar Complete', highlighted = true
    WHERE code ILIKE 'premium';
UPDATE subscription_plans SET is_active = false WHERE code ILIKE 'basic' OR code ILIKE 'standard';

-- Existing-tenant migration: active Basic/Standard/Premium subscribers -> Innovacar Complete label.
UPDATE tenants SET plan_name = 'Innovacar Complete'
    WHERE subscription_active = true AND plan_name IN ('Basic', 'Standard', 'Premium');

-- Move Super-Admin manual account states (BLOCKED/INACTIVE) off the billing-lifecycle
-- status column onto the new orthogonal account_state column, then normalize what's
-- left to a valid SubscriptionStatus enum name. SUSPENDED stays on status (it is now
-- a real billing-lifecycle terminal state, reused by both manual suspend and the
-- automated post-grace-period suspension).
UPDATE tenants SET account_state = 'BLOCKED', status = 'ACTIVE' WHERE status ILIKE 'BLOCKED';
UPDATE tenants SET account_state = 'INACTIVE', status = 'ACTIVE' WHERE status ILIKE 'INACTIVE';

-- status string -> enum name normalization (values SubscriptionStatus will read as @Enumerated(STRING)).
UPDATE tenants SET status = 'CANCEL_AT_PERIOD_END' WHERE status ILIKE 'CANCEL_SCHEDULED';
UPDATE tenants SET status = 'TRIAL_EXPIRED' WHERE status ILIKE 'EXPIRED' AND plan_name = 'Trial';
UPDATE tenants SET status = 'TRIAL_EXPIRED' WHERE status ILIKE 'EXPIRED' AND plan_name IS NULL;
-- Any other legacy EXPIRED (non-trial plan) is a lapsed paid subscription, not a trial.
UPDATE tenants SET status = 'CANCELLED' WHERE status ILIKE 'EXPIRED';
UPDATE tenants SET status = 'GRACE_PERIOD' WHERE status ILIKE 'PAST_DUE';
