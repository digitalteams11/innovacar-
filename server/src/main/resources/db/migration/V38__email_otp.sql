-- ────────────────────────────────────────────────────────────────────────────
-- V38 : Email OTP — persistent OTP codes + user email-OTP fields
-- ────────────────────────────────────────────────────────────────────────────

-- 1. New columns on users table
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_otp_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_email_otp_enabled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_security_change_at   TIMESTAMP;

-- 2. email_otp_codes table
CREATE TABLE IF NOT EXISTS email_otp_codes (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    purpose      VARCHAR(50) NOT NULL,
    code_hash    VARCHAR(255) NOT NULL,
    expires_at   TIMESTAMP   NOT NULL,
    used_at      TIMESTAMP,
    attempts     INTEGER     NOT NULL DEFAULT 0,
    max_attempts INTEGER     NOT NULL DEFAULT 5,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_email_otp_user_purpose ON email_otp_codes(user_id, purpose);
CREATE INDEX IF NOT EXISTS idx_email_otp_expires       ON email_otp_codes(expires_at);
CREATE INDEX IF NOT EXISTS idx_email_otp_user_created  ON email_otp_codes(user_id, created_at);
