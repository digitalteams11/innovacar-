-- Makes SubscriptionPlan.trial_days actually control real trial duration.
-- Previously every trial-creation code path hardcoded exactly one calendar
-- month (Tenant.TRIAL_PERIOD_MONTHS), completely ignoring the per-plan
-- trial_days value that Super Admin could already edit in the UI — the field
-- was persisted and displayed but had zero effect on any tenant's actual
-- trial length.
--
-- A day-count duration (trialDays = 1, 7, 14, 30, 60...) cannot be correctly
-- enforced with date-only columns: comparing LocalDate.now() against a
-- trial_end_date computed as "start date + N days" only flips to expired at
-- the NEXT midnight after that date, silently granting up to one extra day
-- of access regardless of the exact signup time. A 1-day trial must expire
-- exactly 24 hours after signup, not "at the end of tomorrow". These new
-- timestamp columns are the precise source of truth going forward; the
-- existing trial_start_date/trial_end_date (DATE) columns are left in place
-- untouched for any code that still reads them, and are backfilled here so
-- existing tenants keep an equivalent (day-granularity) value rather than
-- suddenly having a null trial window.

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trial_started_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMP;

UPDATE tenants
SET trial_started_at = trial_start_date::timestamp
WHERE trial_start_date IS NOT NULL AND trial_started_at IS NULL;

UPDATE tenants
SET trial_ends_at = trial_end_date::timestamp
WHERE trial_end_date IS NOT NULL AND trial_ends_at IS NULL;

-- Lets a plan's trial be toggled independently of its trial_days number
-- (e.g. temporarily disable a trial offer without erasing the configured day
-- count) — explicit boolean rather than inferring intent from trial_days
-- alone or from a hardcoded plan-code/name comparison.
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS is_trial_enabled BOOLEAN;
UPDATE subscription_plans SET is_trial_enabled = (COALESCE(trial_days, 0) > 0) WHERE is_trial_enabled IS NULL;
ALTER TABLE subscription_plans ALTER COLUMN is_trial_enabled SET DEFAULT FALSE;
