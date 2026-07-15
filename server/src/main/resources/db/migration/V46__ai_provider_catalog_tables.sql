-- Provider-independent AI architecture: provider catalog table.
-- IF NOT EXISTS: a manually-bootstrapped database (see
-- server/scripts/bootstrap/baseline-core-schema.sql) may already contain
-- this table as part of the current entity shape.
CREATE TABLE IF NOT EXISTS ai_providers (
    id                       BIGSERIAL PRIMARY KEY,
    name                     VARCHAR(120) NOT NULL,
    provider_type            VARCHAR(40)  NOT NULL
        CHECK (provider_type IN ('GROQ', 'GEMINI', 'OPENAI', 'OPENROUTER', 'CUSTOM_OPENAI_COMPATIBLE')),
    base_url                 VARCHAR(500),
    api_key_encrypted        TEXT,
    api_key_masked_hint      VARCHAR(60),
    organization_id          VARCHAR(120),
    enabled                  BOOLEAN NOT NULL DEFAULT TRUE,
    is_active                BOOLEAN NOT NULL DEFAULT FALSE,
    is_fallback              BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted                BOOLEAN NOT NULL DEFAULT FALSE,
    connection_status        VARCHAR(30) NOT NULL DEFAULT 'NOT_TESTED'
        CHECK (connection_status IN ('NOT_TESTED', 'CONNECTED', 'FAILED', 'DISABLED')),
    last_connection_test_at  TIMESTAMP,
    last_connection_error    TEXT,
    last_test_latency_ms     BIGINT,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP,
    created_by                BIGINT,
    updated_by                BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_providers_single_active ON ai_providers (is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_ai_providers_type ON ai_providers (provider_type);
