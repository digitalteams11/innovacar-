-- ============================================================
-- V34 — Missing tables & column gap-fill
-- Safe: CREATE TABLE IF NOT EXISTS, ADD COLUMN IF NOT EXISTS
-- Does NOT drop any table, column, or row.
-- ============================================================

-- ── 1. super_admin_roles ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS super_admin_roles (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL,
    label       VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT uk_super_admin_role_code UNIQUE (code)
);

-- ── 2. super_admin_permission_definitions ────────────────────
CREATE TABLE IF NOT EXISTS super_admin_permission_definitions (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    category    VARCHAR(80)  NOT NULL
);

-- ── 3. super_admin_role_permissions ──────────────────────────
CREATE TABLE IF NOT EXISTS super_admin_role_permissions (
    id              BIGSERIAL    PRIMARY KEY,
    role_id         BIGINT       NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_super_admin_role_permission UNIQUE (role_id, permission_code)
);

-- ── 4. permission_definitions ────────────────────────────────
CREATE TABLE IF NOT EXISTS permission_definitions (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    category    VARCHAR(80)  NOT NULL
);

-- ── 5. role_permissions ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS role_permissions (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    role_name       VARCHAR(40)  NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_tenant_role_permission UNIQUE (tenant_id, role_name, permission_code)
);

-- ── 6. platform_settings ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS platform_settings (
    id                         BIGSERIAL PRIMARY KEY,
    platform_name              VARCHAR(255),
    company_name               VARCHAR(255),
    logo_url                   VARCHAR(2000),
    favicon_url                VARCHAR(2000),
    primary_color              VARCHAR(255),
    secondary_color            VARCHAR(255),
    accent_color               VARCHAR(255),
    default_timezone           VARCHAR(255),
    support_email              VARCHAR(255),
    support_phone              VARCHAR(255),
    legal_company_name         VARCHAR(255),
    legal_address              VARCHAR(1000),
    website_url                VARCHAR(255),
    maintenance_mode           BOOLEAN,
    maintenance_message        VARCHAR(2000),
    default_language           VARCHAR(255),
    supported_languages        VARCHAR(255),
    default_currency           VARCHAR(255),
    smtp_host                  VARCHAR(255),
    smtp_port                  INTEGER,
    smtp_username              VARCHAR(255),
    smtp_password_encrypted    VARCHAR(255),
    smtp_use_tls               BOOLEAN,
    smtp_enabled               BOOLEAN,
    smtp_reply_to              VARCHAR(150),
    last_smtp_test_status      VARCHAR(20),
    last_smtp_test_at          TIMESTAMP,
    last_smtp_test_error_code  VARCHAR(100),
    smtp_provider              VARCHAR(20),
    from_email                 VARCHAR(255),
    from_name                  VARCHAR(255),
    api_rate_limit             INTEGER,
    session_timeout_minutes    INTEGER,
    max_login_attempts         INTEGER,
    lockout_duration_minutes   INTEGER,
    require_2fa                BOOLEAN,
    analytics_id               VARCHAR(255),
    custom_css                 VARCHAR(5000),
    theme_presets_json         TEXT,
    marketing_onboarding_json  TEXT,
    updated_at                 TIMESTAMP
);

-- ── 7. support_tickets ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS support_tickets (
    id             BIGSERIAL    PRIMARY KEY,
    ticket_number  VARCHAR(100) UNIQUE,
    subject        VARCHAR(255) NOT NULL,
    description    VARCHAR(2000),
    status         VARCHAR(50)  NOT NULL,
    priority       VARCHAR(50)  NOT NULL,
    category       VARCHAR(100),
    tenant_id      BIGINT,
    created_by     VARCHAR(255),
    contact_email  VARCHAR(255),
    assigned_to    VARCHAR(255),
    resolution     VARCHAR(2000),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    resolved_at    TIMESTAMP
);

-- ── 8. support_messages ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS support_messages (
    id              BIGSERIAL    PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL,
    sender_id       BIGINT,
    sender_name     VARCHAR(255) NOT NULL,
    sender_type     VARCHAR(50)  NOT NULL,
    message         VARCHAR(4000),
    attachment_name VARCHAR(255),
    attachment_type VARCHAR(255),
    attachment_data TEXT,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_support_message_ticket ON support_messages (ticket_id, created_at);

-- ── 9. onboarding_progress ───────────────────────────────────
CREATE TABLE IF NOT EXISTS onboarding_progress (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT    NOT NULL UNIQUE,
    welcome_dismissed BOOLEAN   NOT NULL DEFAULT FALSE,
    wizard_skipped    BOOLEAN   NOT NULL DEFAULT FALSE,
    completed         BOOLEAN   NOT NULL DEFAULT FALSE,
    tour_completed    BOOLEAN   NOT NULL DEFAULT FALSE,
    completed_at      TIMESTAMP,
    updated_at        TIMESTAMP
);

-- ── 10. trusted_devices ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS trusted_devices (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    tenant_id        BIGINT       NOT NULL,
    fingerprint_hash VARCHAR(128) NOT NULL,
    device_name      VARCHAR(150),
    browser          VARCHAR(80),
    operating_system VARCHAR(80),
    last_ip_address  VARCHAR(64),
    trusted          BOOLEAN      NOT NULL DEFAULT FALSE,
    blocked          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_seen_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    trusted_at       TIMESTAMP,
    expires_at       TIMESTAMP,
    revoked_at       TIMESTAMP,
    last_used_at     TIMESTAMP,
    CONSTRAINT uq_trusted_device_user_fp UNIQUE (user_id, fingerprint_hash)
);

CREATE INDEX IF NOT EXISTS idx_trusted_device_user   ON trusted_devices (user_id);
CREATE INDEX IF NOT EXISTS idx_trusted_device_tenant ON trusted_devices (tenant_id);

-- ── 11. password_history ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS password_history (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_history_user ON password_history (user_id);

-- ── 12. automation_rules ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS automation_rules (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(500),
    trigger_type  VARCHAR(50)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by_id BIGINT,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_automation_rule_tenant ON automation_rules (tenant_id, trigger_type, active);

-- ── 13. automation_actions ────────────────────────────────────
CREATE TABLE IF NOT EXISTS automation_actions (
    id                 BIGSERIAL   PRIMARY KEY,
    rule_id            BIGINT      NOT NULL,
    action_type        VARCHAR(50) NOT NULL,
    position           INTEGER     NOT NULL,
    configuration_json TEXT        NOT NULL,
    CONSTRAINT uk_automation_action_position UNIQUE (rule_id, position)
);

CREATE INDEX IF NOT EXISTS idx_automation_action_rule ON automation_actions (rule_id);

-- ── 14. automation_executions ─────────────────────────────────
CREATE TABLE IF NOT EXISTS automation_executions (
    id             BIGSERIAL    PRIMARY KEY,
    rule_id        BIGINT       NOT NULL,
    tenant_id      BIGINT       NOT NULL,
    trigger_type   VARCHAR(50)  NOT NULL,
    event_key      VARCHAR(180) NOT NULL,
    source_type    VARCHAR(60)  NOT NULL,
    source_id      BIGINT,
    status         VARCHAR(20)  NOT NULL,
    context_json   TEXT,
    error_message  VARCHAR(1000),
    started_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMP,
    CONSTRAINT uk_automation_execution_event UNIQUE (rule_id, event_key)
);

CREATE INDEX IF NOT EXISTS idx_automation_execution_tenant ON automation_executions (tenant_id, started_at);

-- ── 15. backup_records ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backup_records (
    id            BIGSERIAL    PRIMARY KEY,
    type          VARCHAR(30)  NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    file_name     VARCHAR(255),
    file_path     VARCHAR(1000),
    size_bytes    BIGINT,
    sha256        VARCHAR(64),
    created_by_id BIGINT,
    created_by    VARCHAR(255),
    error_message VARCHAR(2000),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMP,
    restored_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_backup_created ON backup_records (created_at);
CREATE INDEX IF NOT EXISTS idx_backup_status  ON backup_records (status);

-- ── 16. announcements ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS announcements (
    id             BIGSERIAL    PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    message        TEXT         NOT NULL,
    audience       VARCHAR(30)  NOT NULL,
    audience_value VARCHAR(500),
    priority       VARCHAR(20)  NOT NULL,
    starts_at      TIMESTAMP,
    ends_at        TIMESTAMP,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by     VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP
);

-- ── 17. notification_reads ────────────────────────────────────
CREATE TABLE IF NOT EXISTS notification_reads (
    id              BIGSERIAL PRIMARY KEY,
    admin_user_id   BIGINT    NOT NULL,
    notification_id BIGINT    NOT NULL,
    read_at         TIMESTAMP,
    CONSTRAINT uq_notification_read UNIQUE (admin_user_id, notification_id)
);

-- ── 18. legal_documents ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS legal_documents (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(100) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    version      VARCHAR(50)  NOT NULL,
    content      TEXT         NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    published_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_legal_document_code_version UNIQUE (code, version)
);

-- ── 19. legal_acceptances ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS legal_acceptances (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    document_id BIGINT       NOT NULL,
    ip_address  VARCHAR(255),
    user_agent  VARCHAR(500),
    accepted_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_legal_acceptance_user_document UNIQUE (user_id, document_id)
);

CREATE INDEX IF NOT EXISTS idx_legal_acceptance_tenant ON legal_acceptances (tenant_id);
CREATE INDEX IF NOT EXISTS idx_legal_acceptance_user   ON legal_acceptances (user_id);

-- ── 20. knowledge_articles ────────────────────────────────────
CREATE TABLE IF NOT EXISTS knowledge_articles (
    id         BIGSERIAL    PRIMARY KEY,
    slug       VARCHAR(255) NOT NULL UNIQUE,
    title      VARCHAR(255) NOT NULL,
    category   VARCHAR(100) NOT NULL,
    summary    VARCHAR(500),
    content    TEXT         NOT NULL,
    published  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── 21. agency_balance_transactions ──────────────────────────
CREATE TABLE IF NOT EXISTS agency_balance_transactions (
    id            BIGSERIAL      PRIMARY KEY,
    tenant_id     BIGINT         NOT NULL,
    type          VARCHAR(20)    NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL,
    balance_after NUMERIC(12, 2) NOT NULL,
    reason        VARCHAR(500)   NOT NULL,
    reference     VARCHAR(200),
    created_by    VARCHAR(255),
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agency_balance_tenant   ON agency_balance_transactions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agency_balance_created  ON agency_balance_transactions (created_at);

-- ── 22. data_reset_audit_logs ─────────────────────────────────
CREATE TABLE IF NOT EXISTS data_reset_audit_logs (
    id                   BIGSERIAL    PRIMARY KEY,
    scope                VARCHAR(20)  NOT NULL,
    action               VARCHAR(50)  NOT NULL,
    tenant_id            BIGINT,
    tenant_name          VARCHAR(200),
    client_id            BIGINT,
    status               VARCHAR(20)  NOT NULL,
    performed_by_id      BIGINT,
    performed_by_email   VARCHAR(200),
    ip_address           VARCHAR(64),
    request_summary      VARCHAR(2000),
    result_summary       VARCHAR(2000),
    error_message        VARCHAR(1000),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_data_reset_audit_tenant  ON data_reset_audit_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_data_reset_audit_created ON data_reset_audit_logs (created_at);

-- ── 23. white_label_settings ──────────────────────────────────
CREATE TABLE IF NOT EXISTS white_label_settings (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    logo_url      TEXT,
    primary_color VARCHAR(20),
    accent_color  VARCHAR(20),
    custom_domain VARCHAR(255) UNIQUE,
    domain_status VARCHAR(30),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_white_label_tenant ON white_label_settings (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_white_label_domain ON white_label_settings (custom_domain);

-- ── 24. cancellation_requests ─────────────────────────────────
CREATE TABLE IF NOT EXISTS cancellation_requests (
    id                    BIGSERIAL    PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL,
    requested_by_user_id  BIGINT       NOT NULL,
    reason                VARCHAR(40)  NOT NULL,
    feedback              VARCHAR(1000),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by           VARCHAR(255),
    reviewed_at           TIMESTAMP,
    review_note           VARCHAR(1000),
    created_at            TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cancellation_request_tenant ON cancellation_requests (tenant_id);

-- ── 25. phone_otps ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS phone_otps (
    id           BIGSERIAL   PRIMARY KEY,
    phone_number VARCHAR(32) NOT NULL,
    otp_code     VARCHAR(8)  NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP   NOT NULL,
    verified     BOOLEAN     DEFAULT FALSE,
    attempts     INTEGER     DEFAULT 0
);

-- ── 26. subscription_events ───────────────────────────────────
-- Already in V15 but guard here for safety
CREATE TABLE IF NOT EXISTS subscription_events (
    id             BIGSERIAL    PRIMARY KEY,
    whop_event_id  VARCHAR(255) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    tenant_id      BIGINT,
    membership_id  VARCHAR(255),
    plan_code      VARCHAR(100),
    raw_payload    TEXT,
    processed_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_whop_event_id UNIQUE (whop_event_id)
);

-- ── 27. Column additions on existing tables ───────────────────

-- tenants: balance, free_access, verification fields
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS balance            NUMERIC(12, 2) DEFAULT 0;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS free_access_until  DATE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS free_access_reason VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS verified_at        TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS verified_by        BIGINT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS custom_monthly_price NUMERIC(10, 2);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS custom_price_note  VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS verification_status VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS status             VARCHAR(255);

-- users: super_admin_role_id link
ALTER TABLE users ADD COLUMN IF NOT EXISTS super_admin_role_id BIGINT;

-- promo_code_redemptions: extended lifecycle fields
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS plan_id         BIGINT;
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS billing_cycle   VARCHAR(20);
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS original_price  NUMERIC(10, 2);
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10, 2);
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS final_price     NUMERIC(10, 2);

-- affiliate_referrals: fields added after V7
ALTER TABLE affiliate_referrals ADD COLUMN IF NOT EXISTS referred_tenant_id  BIGINT;
ALTER TABLE affiliate_referrals ADD COLUMN IF NOT EXISTS reward_type          VARCHAR(30);
ALTER TABLE affiliate_referrals ADD COLUMN IF NOT EXISTS free_months_awarded  INTEGER;
ALTER TABLE affiliate_referrals ADD COLUMN IF NOT EXISTS commission_amount    NUMERIC(10, 2);
ALTER TABLE affiliate_referrals ADD COLUMN IF NOT EXISTS converted_at         TIMESTAMP;

-- affiliate_rules: name field
ALTER TABLE affiliate_rules ADD COLUMN IF NOT EXISTS name VARCHAR(80);

-- vehicles: additional condition/document fields
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS condition_status                      VARCHAR(30);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS last_inspection_at                    TIMESTAMP;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS last_returned_at                      TIMESTAMP;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS license_expiry_date                   DATE;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS circulation_authorization_expiry_date DATE;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS out_of_zone                           BOOLEAN;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS last_speed                            DOUBLE PRECISION;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS gps_enabled                           BOOLEAN;

-- tenant_settings: inspection retention + json config columns
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS inspection_retention_days INTEGER NOT NULL DEFAULT 7;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS appearance_json           TEXT;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS sound_settings_json       TEXT;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS security_settings_json    TEXT;

-- subscription_invoices: missing NOT NULL enforcement fix
ALTER TABLE subscription_invoices ADD COLUMN IF NOT EXISTS plan_id BIGINT;

-- gps_settings: polling interval and geofence notification flags
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS enabled               BOOLEAN DEFAULT TRUE;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS city_lat              DOUBLE PRECISION;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS city_lng              DOUBLE PRECISION;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS radius_km             DOUBLE PRECISION;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS movement_threshold_m  INTEGER;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS inactivity_timeout_min INTEGER;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS notify_movement       BOOLEAN;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS notify_geofence       BOOLEAN;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS notify_offline        BOOLEAN;
ALTER TABLE gps_settings ADD COLUMN IF NOT EXISTS polling_interval_sec  INTEGER;

-- ai_settings: test result columns
ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS last_test_error_code VARCHAR(50);
ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS last_test_message     VARCHAR(500);
