-- Upgrade password_reset_tokens to support 6-digit email code flow
-- with a short-lived resetSessionToken issued after code verification.
ALTER TABLE password_reset_tokens
    ALTER COLUMN token_hash DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS code_hash                  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS reset_session_token_hash   VARCHAR(512),
    ADD COLUMN IF NOT EXISTS reset_session_expires_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS attempts                   INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status                     VARCHAR(20) NOT NULL DEFAULT 'USED',
    ADD COLUMN IF NOT EXISTS ip_address                 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS user_agent                 VARCHAR(512);

-- Existing rows already consumed (link flow) — mark them USED so they never surface in code queries.
UPDATE password_reset_tokens SET status = 'USED' WHERE status = 'USED' OR status IS NULL;
