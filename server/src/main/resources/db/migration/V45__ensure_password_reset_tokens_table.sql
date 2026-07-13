-- ============================================================
-- V45 — Defensively ensure password_reset_tokens exists with the
-- full column set the code-based reset flow (V28) expects.
--
-- No migration in this repo actually CREATEs this table — V28 only
-- ALTERs it, which silently assumes it already exists. On any
-- environment where it doesn't (e.g. a freshly provisioned database),
-- password resets fail with an uncaught SQL exception. This migration
-- is fully idempotent (IF NOT EXISTS everywhere) so it is a no-op on
-- environments where the table is already correct.
-- ============================================================

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash                  VARCHAR(512),
    code_hash                   VARCHAR(512),
    reset_session_token_hash    VARCHAR(512),
    reset_session_expires_at    TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    expires_at                  TIMESTAMP NOT NULL,
    used                        BOOLEAN NOT NULL DEFAULT false,
    attempts                    INT NOT NULL DEFAULT 0,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ip_address                  VARCHAR(64),
    user_agent                  VARCHAR(512)
);

-- Re-assert the columns V28 expects, in case an older/partial version
-- of this table exists without them.
ALTER TABLE password_reset_tokens
    ALTER COLUMN token_hash DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS code_hash                  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS reset_session_token_hash   VARCHAR(512),
    ADD COLUMN IF NOT EXISTS reset_session_expires_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS attempts                   INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status                     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS ip_address                 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS user_agent                 VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_pwd_reset_user    ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_expires ON password_reset_tokens (expires_at);
