-- ============================================================
-- V80 — Explicit email-delivery tracking for reports.
--
-- reports.failure_reason (V79) is the report-GENERATION failure — a
-- calculation/PDF error. Email send/resend is a separate lifecycle event
-- with its own failure mode (recipient missing, provider rejection, network
-- error) and must not be conflated with generation failures, otherwise a
-- report that generated successfully but failed to email would overwrite
-- (or be overwritten by) an unrelated generation error message.
-- ============================================================

ALTER TABLE reports ADD COLUMN IF NOT EXISTS email_failure_code VARCHAR(60);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS email_failure_reason VARCHAR(1000);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS email_attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS last_email_attempt_at TIMESTAMP;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(200);
