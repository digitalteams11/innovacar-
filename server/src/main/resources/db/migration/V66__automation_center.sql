-- Automation & AI Agent Center — Phase 1 (real MVP, not the full 10-agent spec):
-- feature-gate the new AUTOMATION_CENTER area behind Premium/Enterprise using the
-- existing generic feature_definitions/plan_features system (same pattern as
-- GPS_TRACKING/AI_ASSISTANT — see FeatureAccessService), plus a minimal schema for
-- 3 real agents that wrap already-working jobs (trial/subscription lifecycle, GPS
-- monitoring, backup verification) with persisted run history instead of adding
-- fake dashboard numbers.

INSERT INTO feature_definitions (code, name, description, benefits, category, active, created_at, updated_at)
VALUES (
    'AUTOMATION_CENTER',
    'AI & Automation Center',
    'Automate contracts, reminders, payments, GPS alerts and customer support.',
    'Automate contracts, reminders, payments, GPS alerts and customer support with Innovacar Premium — smart contract automation, automated reminders, GPS alerts, an AI support assistant, and operational analytics.',
    'AUTOMATION',
    TRUE, NOW(), NOW()
)
ON CONFLICT (code) DO NOTHING;

DO $$
DECLARE v_plan_id BIGINT;
BEGIN
    -- Case-insensitive: FeatureAccessService.resolveTenantPlan() already has to tolerate
    -- casing drift between subscription_plans.code and Tenant.planName in real data (see
    -- its own class javadoc), and at least one existing environment has been observed
    -- with a lowercase 'premium' code alongside an uppercase 'ENTERPRISE' one.
    FOR v_plan_id IN SELECT id FROM subscription_plans WHERE UPPER(code) IN ('PREMIUM', 'ENTERPRISE')
    LOOP
        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (v_plan_id, 'AUTOMATION_CENTER', TRUE, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();
    END LOOP;
END $$;

-- Per-tenant agent state — lazily created on first access (see AutomationService),
-- not pre-seeded for every tenant. Only the 3 agents actually implemented in this
-- phase are ever inserted; the other 7 from the full spec are not represented here
-- yet so the UI never shows a catalog entry that doesn't really do anything.
CREATE TABLE IF NOT EXISTS automation_agents (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT REFERENCES tenants(id) ON DELETE CASCADE,
    agent_key        VARCHAR(60) NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PAUSED', 'DEGRADED', 'ERROR', 'DISABLED', 'REQUIRES_CONFIGURATION')),
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at      TIMESTAMP,
    next_run_at      TIMESTAMP,
    last_success_at  TIMESTAMP,
    last_failure_at  TIMESTAMP,
    success_count    BIGINT NOT NULL DEFAULT 0,
    failure_count    BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, agent_key)
);

CREATE INDEX IF NOT EXISTS idx_automation_agents_tenant ON automation_agents (tenant_id);

CREATE TABLE IF NOT EXISTS automation_runs (
    id                      BIGSERIAL PRIMARY KEY,
    -- Nullable: some agents (subscription/trial batch, platform backups) run once
    -- platform-wide rather than once per tenant — see AutomationRunRecorder javadoc.
    tenant_id               BIGINT REFERENCES tenants(id) ON DELETE CASCADE,
    agent_key               VARCHAR(60) NOT NULL,
    status                  VARCHAR(30) NOT NULL
        CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')),
    started_at              TIMESTAMP NOT NULL,
    completed_at            TIMESTAMP,
    duration_ms             BIGINT,
    result_summary          VARCHAR(500),
    error_code              VARCHAR(60),
    sanitized_error_message VARCHAR(1000),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_automation_runs_tenant ON automation_runs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_automation_runs_agent ON automation_runs (agent_key);
CREATE INDEX IF NOT EXISTS idx_automation_runs_created ON automation_runs (created_at);

CREATE TABLE IF NOT EXISTS automation_alerts (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT REFERENCES tenants(id) ON DELETE CASCADE,
    agent_key        VARCHAR(60) NOT NULL,
    severity         VARCHAR(20) NOT NULL DEFAULT 'INFO'
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title            VARCHAR(200) NOT NULL,
    message          VARCHAR(1000),
    acknowledged     BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by  BIGINT,
    acknowledged_at  TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_automation_alerts_tenant ON automation_alerts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_automation_alerts_ack ON automation_alerts (acknowledged);
