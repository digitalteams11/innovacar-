-- Subscription event log for webhook idempotency and audit trail.
-- Each row represents one Whop webhook event that has been processed.
-- Before processing any event, WhopWebhookController checks this table
-- to skip duplicates (same event_id means already handled).

CREATE TABLE IF NOT EXISTS subscription_events (
    id               BIGSERIAL PRIMARY KEY,
    whop_event_id    VARCHAR(255) NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    tenant_id        BIGINT       REFERENCES tenants(id) ON DELETE SET NULL,
    membership_id    VARCHAR(255),
    plan_code        VARCHAR(100),
    raw_payload      TEXT,
    processed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_whop_event_id UNIQUE (whop_event_id)
);

CREATE INDEX IF NOT EXISTS idx_subscription_events_tenant  ON subscription_events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_events_type    ON subscription_events (event_type);
CREATE INDEX IF NOT EXISTS idx_subscription_events_processed ON subscription_events (processed_at DESC);
