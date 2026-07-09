-- Store 2FA pending setup secret server-side so the same QR code is
-- reused within a 10-minute window instead of regenerating on every page load.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS pending_two_factor_secret_encrypted VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS pending_two_factor_setup_at        TIMESTAMP;
