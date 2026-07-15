-- IF NOT EXISTS: a manually-bootstrapped database (see
-- server/scripts/bootstrap/baseline-core-schema.sql) may already contain
-- this table as part of the current entity shape.
CREATE TABLE IF NOT EXISTS ai_models (
    id                       BIGSERIAL PRIMARY KEY,
    ai_provider_id           BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    model_id                 VARCHAR(120) NOT NULL,
    display_name             VARCHAR(160),
    enabled                  BOOLEAN NOT NULL DEFAULT TRUE,
    default_model            BOOLEAN NOT NULL DEFAULT FALSE,
    default_vision_model     BOOLEAN NOT NULL DEFAULT FALSE,
    context_window           BIGINT,
    input_price_per_million  NUMERIC(12, 4),
    output_price_per_million NUMERIC(12, 4),
    supports_streaming       BOOLEAN NOT NULL DEFAULT FALSE,
    supports_json_mode       BOOLEAN NOT NULL DEFAULT FALSE,
    supports_tool_calling    BOOLEAN NOT NULL DEFAULT FALSE,
    source                   VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('SYNCED', 'MANUAL')),
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP,
    CONSTRAINT uq_ai_models_provider_model UNIQUE (ai_provider_id, model_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_models_provider ON ai_models (ai_provider_id);
