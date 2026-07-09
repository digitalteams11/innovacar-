-- Add recovery codes storage and confirmed-at timestamp for 2FA
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS two_factor_confirmed_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS two_factor_recovery_codes_hash TEXT;
