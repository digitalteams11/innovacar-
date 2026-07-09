-- ============================================================
-- V30 — SMTP provider field, last test error code,
--        and code-based email verification support.
-- Safe: ADD COLUMN IF NOT EXISTS, no data loss.
-- ============================================================

-- 1. Add smtp_provider (ZOHO / GMAIL / CUSTOM) and persistent last-test error code
ALTER TABLE platform_settings
    ADD COLUMN IF NOT EXISTS smtp_provider              VARCHAR(20)  DEFAULT 'ZOHO',
    ADD COLUMN IF NOT EXISTS last_smtp_test_error_code  VARCHAR(100);

-- 2. Auto-enable SMTP for already-configured instances so existing email delivery
--    continues to work after SmtpMailService starts respecting the smtpEnabled flag.
UPDATE platform_settings
SET    smtp_enabled = TRUE
WHERE  smtp_password_encrypted IS NOT NULL
  AND  smtp_host IS NOT NULL
  AND  smtp_username IS NOT NULL
  AND  (smtp_enabled IS NULL OR smtp_enabled = FALSE);

-- 3. Add code-based email verification fields to email_verification_tokens
ALTER TABLE email_verification_tokens
    ADD COLUMN IF NOT EXISTS verification_code_hash      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS verification_code_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS verification_code_attempts  INTEGER DEFAULT 0;
