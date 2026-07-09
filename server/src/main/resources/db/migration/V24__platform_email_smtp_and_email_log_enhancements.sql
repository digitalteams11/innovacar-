-- ============================================================
-- V24 — Platform SMTP controls + email_logs contract tracking
-- Safe: ALTER TABLE ADD COLUMN IF NOT EXISTS, no data loss.
-- ============================================================

-- 1. Extend platform_settings with SMTP on/off toggle, reply-to,
--    and last-test metadata that Super Admin email center needs.
ALTER TABLE platform_settings
    ADD COLUMN IF NOT EXISTS smtp_enabled           BOOLEAN     DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS smtp_reply_to          VARCHAR(150),
    ADD COLUMN IF NOT EXISTS last_smtp_test_status  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS last_smtp_test_at      TIMESTAMP;

-- 2. Extend email_logs so every row is traceable to a tenant and contract,
--    carries a semantic type, and records a machine-readable error code.
ALTER TABLE email_logs
    ADD COLUMN IF NOT EXISTS tenant_id    BIGINT,
    ADD COLUMN IF NOT EXISTS contract_id  BIGINT,
    ADD COLUMN IF NOT EXISTS email_type   VARCHAR(60),
    ADD COLUMN IF NOT EXISTS error_code   VARCHAR(60);

-- Indexes for common lookups (contract email status, tenant logs).
CREATE INDEX IF NOT EXISTS idx_email_logs_contract ON email_logs (contract_id);
CREATE INDEX IF NOT EXISTS idx_email_logs_tenant   ON email_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_email_logs_type     ON email_logs (email_type);
