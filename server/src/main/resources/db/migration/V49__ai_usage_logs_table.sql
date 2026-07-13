CREATE TABLE ai_usage_logs (
    id               BIGSERIAL PRIMARY KEY,
    ai_provider_id   BIGINT REFERENCES ai_providers(id) ON DELETE SET NULL,
    ai_model_id      BIGINT REFERENCES ai_models(id) ON DELETE SET NULL,
    automation_code  VARCHAR(60),
    agency_id        BIGINT,
    user_id          BIGINT,
    role             VARCHAR(40),
    request_id       VARCHAR(60),
    status           VARCHAR(30) NOT NULL
        CHECK (status IN ('SUCCESS', 'FAILED', 'BLOCKED', 'RATE_LIMITED')),
    input_tokens     BIGINT,
    output_tokens    BIGINT,
    total_tokens     BIGINT,
    estimated_cost   NUMERIC(12, 6),
    latency_ms       BIGINT,
    error_code       VARCHAR(60),
    error_message    VARCHAR(500),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_usage_agency ON ai_usage_logs (agency_id);
CREATE INDEX idx_ai_usage_user ON ai_usage_logs (user_id);
CREATE INDEX idx_ai_usage_created ON ai_usage_logs (created_at);
CREATE INDEX idx_ai_usage_provider ON ai_usage_logs (ai_provider_id);
CREATE INDEX idx_ai_usage_automation ON ai_usage_logs (automation_code);
