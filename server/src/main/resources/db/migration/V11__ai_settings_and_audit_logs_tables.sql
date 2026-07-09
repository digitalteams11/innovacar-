-- ============================================================
-- V11 — Ensure ai_settings and ai_audit_logs tables exist
-- Safe: CREATE TABLE IF NOT EXISTS — no data loss if already created by JPA.
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_settings (
    id                              BIGSERIAL PRIMARY KEY,
    enabled                         BOOLEAN NOT NULL DEFAULT FALSE,
    provider                        VARCHAR(40) NOT NULL DEFAULT 'GEMINI',
    api_key_encrypted               TEXT,
    text_model                      VARCHAR(80) DEFAULT 'gemini-2.0-flash',
    vision_model                    VARCHAR(80) DEFAULT 'gemini-2.0-flash',
    timeout_seconds                 INTEGER DEFAULT 30,
    max_tokens                      INTEGER DEFAULT 4096,
    temperature                     DOUBLE PRECISION DEFAULT 0.4,
    enable_chat                     BOOLEAN NOT NULL DEFAULT TRUE,
    enable_reports                  BOOLEAN NOT NULL DEFAULT TRUE,
    enable_translations             BOOLEAN NOT NULL DEFAULT TRUE,
    enable_support_assistant        BOOLEAN NOT NULL DEFAULT TRUE,
    enable_guide_generator          BOOLEAN NOT NULL DEFAULT TRUE,
    enable_automation_suggestions   BOOLEAN NOT NULL DEFAULT TRUE,
    enable_image_generation         BOOLEAN NOT NULL DEFAULT FALSE,
    monthly_token_limit             BIGINT DEFAULT 2000000,
    daily_request_limit             INTEGER DEFAULT 200,
    audit_all_actions               BOOLEAN NOT NULL DEFAULT TRUE,
    last_tested_at                  TIMESTAMP,
    last_test_success               BOOLEAN DEFAULT FALSE,
    last_test_message               VARCHAR(500),
    updated_at                      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_audit_logs (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT,
    agency_id               BIGINT,
    role                    VARCHAR(30),
    feature                 VARCHAR(50) NOT NULL,
    prompt_category         VARCHAR(100),
    model                   VARCHAR(80),
    input_tokens_estimate   INTEGER,
    output_tokens_estimate  INTEGER,
    status                  VARCHAR(20) NOT NULL,
    error_code              VARCHAR(50),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_audit_user   ON ai_audit_logs (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_audit_agency ON ai_audit_logs (agency_id, created_at);
