-- Repairs trial dates left over from the old 60-day / 2-month trial logic,
-- and adds reminder-dedup tracking columns used by TrialExpiryJob.
--
-- Safe to re-run: recomputing trial_end_date from the same trial_start_date
-- always yields the same result, and the EXPIRED transition below only ever
-- moves a tenant forward (TRIAL -> EXPIRED), never back. ACTIVE, CANCELLED,
-- SUSPENDED, CANCEL_SCHEDULED and BLOCKED tenants are untouched throughout.

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trial_reminder_7_sent_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trial_reminder_3_sent_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trial_reminder_1_sent_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trial_expired_notified_at TIMESTAMP;

-- Backfill trial_start_date from created_at for any TRIAL tenant missing it
-- (the authoritative source of "when the trial started" is account creation,
-- never "today").
UPDATE tenants
   SET trial_start_date = created_at::date
 WHERE status = 'TRIAL'
   AND trial_start_date IS NULL
   AND created_at IS NOT NULL;

-- Recompute trial_end_date as exactly 1 calendar month after trial_start_date
-- for every tenant still in TRIAL, replacing any stale 60-day/2-month value.
UPDATE tenants
   SET trial_end_date = (trial_start_date + INTERVAL '1 month')::date
 WHERE status = 'TRIAL'
   AND trial_start_date IS NOT NULL;

-- A trial whose recomputed end date has already passed becomes EXPIRED —
-- never re-extend it, and never touch tenants that aren't currently TRIAL.
UPDATE tenants
   SET status = 'EXPIRED',
       subscription_active = false
 WHERE status = 'TRIAL'
   AND trial_end_date IS NOT NULL
   AND trial_end_date < CURRENT_DATE;

-- subscription_end_date means "paid-plan renewal date" and must be empty while
-- a tenant is still (genuinely) on trial — a non-null value here, left over
-- from the old signup flow that set it equal to trial_end_date, is what made
-- the billing card show a stale "Renews on <date>" line for trial tenants.
UPDATE tenants
   SET subscription_end_date = NULL
 WHERE status = 'TRIAL'
   AND subscription_end_date IS NOT NULL;
