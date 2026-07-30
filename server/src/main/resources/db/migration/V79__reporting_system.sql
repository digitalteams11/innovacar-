-- ============================================================
-- V79 — Automated financial/operational reporting system.
--
-- Per product decision: the existing TRIAL/BASIC/STANDARD/PREMIUM/ENTERPRISE
-- plans are NOT collapsed or renamed here (that would touch live Whop
-- checkout products and at least one real tenant's assigned plan — a
-- deliberate non-destructive choice, not an oversight). COMPLETE is added as
-- a new top-tier plan carrying the new report feature codes; PREMIUM and
-- ENTERPRISE (the existing top tiers) are granted the same report features
-- so no current paying customer loses capability. Retiring
-- STANDARD/PREMIUM/ENTERPRISE from new signups, if ever desired, is a
-- separate future migration with its own tenant-remapping plan.
-- ============================================================

-- ── 1. New feature codes (per spec section 1) ────────────────────────────────

INSERT INTO feature_definitions (code, name, description, benefits, category, active) VALUES
    ('AUTOMATED_MONTHLY_REPORT', 'Automated Monthly Report', 'Automatic monthly PDF financial/operational report', 'A complete monthly report generated and emailed automatically after each month closes', 'Reporting', TRUE),
    ('AUTOMATED_YEARLY_REPORT',  'Automated Yearly Report',  'Automatic yearly PDF financial/operational report',  'A complete annual report generated and emailed automatically after each year closes', 'Reporting', TRUE),
    ('AI_REPORT_SUMMARY',        'AI Report Summary',        'AI-written executive summary inside reports',        'A natural-language executive summary of each report, generated from verified figures', 'Reporting', TRUE),
    ('REPORT_ARCHIVE',           'Report Archive',           'Access to the report archive page',                  'Browse, filter, download, and resend every past report', 'Reporting', TRUE),
    ('MANUAL_REPORT_EXPORT',     'Manual Report Generation',  'On-demand report generation outside the schedule',    'Generate a report for any closed period on demand, without waiting for the schedule', 'Reporting', TRUE)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description,
    benefits    = EXCLUDED.benefits,
    category    = EXCLUDED.category,
    active      = EXCLUDED.active,
    updated_at  = NOW();

-- ── 2. COMPLETE plan — mirrors PREMIUM's limits/pricing as the new top tier ──

INSERT INTO subscription_plans
    (name, code, description, monthly_price, yearly_price, currency, trial_days, is_trial_enabled,
     max_vehicles, max_employees, max_gps_devices, max_reservations, client_limit, contract_limit,
     storage_limit_mb, api_access, white_label, priority_support, is_active, display_order,
     billing_cycle_allowed_monthly, billing_cycle_allowed_yearly, created_at, updated_at)
VALUES ('Complete', 'COMPLETE', 'All Basic features plus automated financial reporting, AI analysis, and the full report archive.',
        799, 7990, 'MAD', 0, FALSE,
        200, 60, 100, 10000, 10000, 10000,
        102400, TRUE, TRUE, TRUE, TRUE, 6, TRUE, TRUE, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- ── 3. Grant the core (non-report) feature set to COMPLETE, matching PREMIUM,
--       plus every report feature code. PREMIUM/ENTERPRISE additionally get
--       just the new report codes so existing top-tier customers are not
--       regressed by this migration. ────────────────────────────────────────

DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE UPPER(code) = 'COMPLETE';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'VEHICLE_MANAGEMENT',        TRUE,NOW(),NOW()),
        (v_id,'CLIENT_MANAGEMENT',         TRUE,NOW(),NOW()),
        (v_id,'RESERVATION_MANAGEMENT',    TRUE,NOW(),NOW()),
        (v_id,'CONTRACT_MANAGEMENT',       TRUE,NOW(),NOW()),
        (v_id,'PDF_EXPORT',                TRUE,NOW(),NOW()),
        (v_id,'QR_SIGNATURE',              TRUE,NOW(),NOW()),
        (v_id,'INSPECTION_MEDIA',          TRUE,NOW(),NOW()),
        (v_id,'CUSTOM_TEMPLATES',          TRUE,NOW(),NOW()),
        (v_id,'MULTI_EMPLOYEE',            TRUE,NOW(),NOW()),
        (v_id,'MULTI_BRANCH',              TRUE,NOW(),NOW()),
        (v_id,'INVOICE_GENERATION',        TRUE,NOW(),NOW()),
        (v_id,'PAYMENTS',                  TRUE,NOW(),NOW()),
        (v_id,'REPORTS_BASIC',             TRUE,NOW(),NOW()),
        (v_id,'ADVANCED_REPORTS',          TRUE,NOW(),NOW()),
        (v_id,'GPS_TRACKING',              TRUE,NOW(),NOW()),
        (v_id,'GPS_ALERTS',                TRUE,NOW(),NOW()),
        (v_id,'AI_ASSISTANT',              TRUE,NOW(),NOW()),
        (v_id,'AI_REPORTS',                TRUE,NOW(),NOW()),
        (v_id,'AI_TRANSLATIONS',           TRUE,NOW(),NOW()),
        (v_id,'WHITE_LABEL',               TRUE,NOW(),NOW()),
        (v_id,'PRIORITY_SUPPORT',          TRUE,NOW(),NOW()),
        (v_id,'API_ACCESS',                TRUE,NOW(),NOW()),
        (v_id,'AUTOMATED_MONTHLY_REPORT',  TRUE,NOW(),NOW()),
        (v_id,'AUTOMATED_YEARLY_REPORT',   TRUE,NOW(),NOW()),
        (v_id,'AI_REPORT_SUMMARY',         TRUE,NOW(),NOW()),
        (v_id,'REPORT_ARCHIVE',            TRUE,NOW(),NOW()),
        (v_id,'MANUAL_REPORT_EXPORT',      TRUE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;

DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE UPPER(code) = 'PREMIUM';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'AUTOMATED_MONTHLY_REPORT',  TRUE,NOW(),NOW()),
        (v_id,'AUTOMATED_YEARLY_REPORT',   TRUE,NOW(),NOW()),
        (v_id,'AI_REPORT_SUMMARY',         TRUE,NOW(),NOW()),
        (v_id,'REPORT_ARCHIVE',            TRUE,NOW(),NOW()),
        (v_id,'MANUAL_REPORT_EXPORT',      TRUE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;

DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE UPPER(code) = 'ENTERPRISE';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'AUTOMATED_MONTHLY_REPORT',  TRUE,NOW(),NOW()),
        (v_id,'AUTOMATED_YEARLY_REPORT',   TRUE,NOW(),NOW()),
        (v_id,'AI_REPORT_SUMMARY',         TRUE,NOW(),NOW()),
        (v_id,'REPORT_ARCHIVE',            TRUE,NOW(),NOW()),
        (v_id,'MANUAL_REPORT_EXPORT',      TRUE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;

-- All other existing plans (TRIAL/BASIC/STANDARD) explicitly get the new
-- codes disabled — makes the entitlement state for every plan explicit
-- rather than "absent row = implicitly false", matching this table's
-- existing convention throughout V20/V52.
DO $$
DECLARE v_id BIGINT;
BEGIN
    FOR v_id IN SELECT id FROM subscription_plans WHERE UPPER(code) IN ('TRIAL', 'BASIC', 'STANDARD')
    LOOP
        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
            (v_id,'AUTOMATED_MONTHLY_REPORT',  FALSE,NOW(),NOW()),
            (v_id,'AUTOMATED_YEARLY_REPORT',   FALSE,NOW(),NOW()),
            (v_id,'AI_REPORT_SUMMARY',         FALSE,NOW(),NOW()),
            (v_id,'REPORT_ARCHIVE',            FALSE,NOW(),NOW()),
            (v_id,'MANUAL_REPORT_EXPORT',      FALSE,NOW(),NOW())
        ON CONFLICT (plan_id, feature_code) DO NOTHING;
    END LOOP;
END $$;

-- ── 4. Report preferences (per-agency settings, section 18) ──────────────────

CREATE TABLE IF NOT EXISTS report_preferences (
    id                              BIGSERIAL PRIMARY KEY,
    tenant_id                       BIGINT       NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    report_enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    monthly_report_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    yearly_report_enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    report_language                 VARCHAR(5)   NOT NULL DEFAULT 'fr',
    timezone                        VARCHAR(60),
    primary_recipient_email         VARCHAR(255),
    additional_recipient_emails     VARCHAR(1000),
    include_ai_summary              BOOLEAN      NOT NULL DEFAULT TRUE,
    include_client_debt_detail      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_preferences_tenant ON report_preferences(tenant_id);

-- ── 5. Reports (metadata only — PDF bytes live on disk, see ReportPdfStorage) ─

CREATE TABLE IF NOT EXISTS reports (
    id                  BIGSERIAL   PRIMARY KEY,
    tenant_id           BIGINT      NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    report_type         VARCHAR(10) NOT NULL,          -- MONTHLY | YEARLY
    period_start        TIMESTAMP   NOT NULL,          -- UTC, inclusive
    period_end          TIMESTAMP   NOT NULL,          -- UTC, exclusive
    language             VARCHAR(5)  NOT NULL DEFAULT 'fr',
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    generated_at         TIMESTAMP,
    generated_by         VARCHAR(30) NOT NULL DEFAULT 'SCHEDULER',  -- SCHEDULER | MANUAL | SUPER_ADMIN
    generated_by_user_id BIGINT,
    file_storage_key     VARCHAR(500),
    file_size_bytes      BIGINT,
    checksum             VARCHAR(128),
    email_status         VARCHAR(20) NOT NULL DEFAULT 'NOT_SENT',   -- NOT_SENT | PENDING | SENT | FAILED
    email_sent_at        TIMESTAMP,
    recipient_emails      VARCHAR(1000),
    ai_summary_used       BOOLEAN     NOT NULL DEFAULT FALSE,
    calculation_version   INTEGER     NOT NULL DEFAULT 1,
    failure_reason        VARCHAR(1000),
    calculation_snapshot   TEXT,        -- serialized ReportDataset JSON, source of truth for re-download
    created_at             TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_report_period UNIQUE (tenant_id, report_type, period_start, period_end)
);

CREATE INDEX IF NOT EXISTS idx_reports_tenant       ON reports(tenant_id);
CREATE INDEX IF NOT EXISTS idx_reports_status        ON reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_tenant_type   ON reports(tenant_id, report_type);
CREATE INDEX IF NOT EXISTS idx_reports_email_status  ON reports(email_status);

-- ── 6. Email delivery attempts — one row per send/resend, drives retry +
--       idempotency tracking independent of the report's own email_status ──

CREATE TABLE IF NOT EXISTS report_email_attempts (
    id            BIGSERIAL   PRIMARY KEY,
    report_id     BIGINT      NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    attempt_no    INTEGER     NOT NULL,
    triggered_by  VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',  -- SYSTEM | MANUAL_RESEND
    recipient_emails VARCHAR(1000) NOT NULL,
    success       BOOLEAN     NOT NULL,
    error_message VARCHAR(1000),
    attempted_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_report_email_attempt UNIQUE (report_id, attempt_no)
);

CREATE INDEX IF NOT EXISTS idx_report_email_attempts_report ON report_email_attempts(report_id);
