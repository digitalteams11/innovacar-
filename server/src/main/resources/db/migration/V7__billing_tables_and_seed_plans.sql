-- ============================================================
-- V7 — Billing tables & seed plans
-- Safe: uses CREATE TABLE IF NOT EXISTS and ADD COLUMN IF NOT EXISTS
-- Does NOT drop any existing table or row.
-- ============================================================

-- ── 1. subscription_plans ────────────────────────────────────

CREATE TABLE IF NOT EXISTS subscription_plans (
    id                           BIGSERIAL PRIMARY KEY,
    name                         VARCHAR(100) NOT NULL UNIQUE,
    code                         VARCHAR(50)  NOT NULL UNIQUE,
    description                  VARCHAR(1000),
    monthly_price                NUMERIC(10,2) NOT NULL DEFAULT 0,
    yearly_price                 NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency                     VARCHAR(10)   NOT NULL DEFAULT 'MAD',
    trial_days                   INTEGER       DEFAULT 0,
    max_vehicles                 INTEGER,
    max_employees                INTEGER,
    max_gps_devices              INTEGER,
    max_reservations             INTEGER,
    client_limit                 INTEGER,
    contract_limit               INTEGER,
    storage_limit_mb             INTEGER,
    billing_cycle_allowed_monthly  BOOLEAN DEFAULT TRUE,
    billing_cycle_allowed_yearly   BOOLEAN DEFAULT TRUE,
    api_access                   BOOLEAN DEFAULT FALSE,
    white_label                  BOOLEAN DEFAULT FALSE,
    priority_support             BOOLEAN DEFAULT FALSE,
    is_active                    BOOLEAN DEFAULT TRUE,
    features_json                VARCHAR(2000),
    display_order                INTEGER,
    whop_checkout_url_monthly    VARCHAR(500),
    whop_checkout_url_yearly     VARCHAR(500),
    whop_product_id              VARCHAR(200),
    whop_plan_id                 VARCHAR(200),
    whop_price_id                VARCHAR(200),
    created_at                   TIMESTAMP,
    updated_at                   TIMESTAMP
);

-- Add columns that may not exist yet (safe ALTER)
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS currency                          VARCHAR(10)  DEFAULT 'MAD';
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS trial_days                        INTEGER      DEFAULT 0;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS client_limit                      INTEGER;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS contract_limit                    INTEGER;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS billing_cycle_allowed_monthly     BOOLEAN      DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS billing_cycle_allowed_yearly      BOOLEAN      DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS whop_product_id                  VARCHAR(200);
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS whop_plan_id                     VARCHAR(200);
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS whop_price_id                    VARCHAR(200);

-- ── 2. promo_codes ───────────────────────────────────────────

CREATE TABLE IF NOT EXISTS promo_codes (
    id                          BIGSERIAL PRIMARY KEY,
    code                        VARCHAR(100) NOT NULL UNIQUE,
    promotion_name              VARCHAR(200),
    promotion_type              VARCHAR(50)  DEFAULT 'DISCOUNT',
    discount_type               VARCHAR(20),
    discount_value              NUMERIC(10,2),
    free_months                 INTEGER,
    free_days                   INTEGER,
    free_feature_code           VARCHAR(100),
    trial_plan_code             VARCHAR(50),
    max_uses                    INTEGER,
    max_uses_per_agency         INTEGER,
    used_count                  INTEGER      DEFAULT 0,
    applicable_plans            VARCHAR(500),
    billing_cycle               VARCHAR(20)  DEFAULT 'BOTH',
    minimum_amount              NUMERIC(10,2),
    currency                    VARCHAR(10)  DEFAULT 'MAD',
    description                 VARCHAR(500),
    created_by_super_admin_id   BIGINT,
    is_active                   BOOLEAN      DEFAULT TRUE,
    valid_from                  DATE,
    valid_to                    DATE,
    created_at                  TIMESTAMP,
    updated_at                  TIMESTAMP
);

ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS promotion_name          VARCHAR(200);
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS promotion_type          VARCHAR(50) DEFAULT 'DISCOUNT';
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS free_months             INTEGER;
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS free_days               INTEGER;
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS free_feature_code       VARCHAR(100);
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS trial_plan_code         VARCHAR(50);
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS max_uses_per_agency     INTEGER;
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS billing_cycle           VARCHAR(20) DEFAULT 'BOTH';
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS minimum_amount          NUMERIC(10,2);
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS created_by_super_admin_id BIGINT;

-- ── 3. promo_code_redemptions ────────────────────────────────

CREATE TABLE IF NOT EXISTS promo_code_redemptions (
    id               BIGSERIAL PRIMARY KEY,
    promo_code_id    BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL,
    subscription_id  BIGINT,
    discount_applied NUMERIC(10,2),
    metadata         TEXT,
    redeemed_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── 4. subscription_invoices ─────────────────────────────────

CREATE TABLE IF NOT EXISTS subscription_invoices (
    id                 BIGSERIAL PRIMARY KEY,
    invoice_number     VARCHAR(100) NOT NULL UNIQUE,
    tenant_id          BIGINT       NOT NULL,
    plan_id            BIGINT,
    billing_cycle      VARCHAR(20),
    subtotal           NUMERIC(10,2),
    discount           NUMERIC(10,2) DEFAULT 0,
    total              NUMERIC(10,2),
    currency           VARCHAR(10)   DEFAULT 'MAD',
    status             VARCHAR(30)   DEFAULT 'PENDING',
    gateway_provider   VARCHAR(50),
    gateway_reference  VARCHAR(200),
    coupon_code        VARCHAR(100),
    issued_at          TIMESTAMP     DEFAULT NOW(),
    paid_at            TIMESTAMP
);

-- ── 5. plan_features ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS plan_features (
    id           BIGSERIAL PRIMARY KEY,
    plan_id      BIGINT      NOT NULL,
    feature_code VARCHAR(80) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT uk_plan_feature UNIQUE (plan_id, feature_code)
);

-- ── 6. tenant_feature_overrides ──────────────────────────────

CREATE TABLE IF NOT EXISTS tenant_feature_overrides (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT      NOT NULL,
    feature_code VARCHAR(80) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    source       VARCHAR(200),
    starts_at    TIMESTAMP,
    expires_at   TIMESTAMP,
    created_at   TIMESTAMP   DEFAULT NOW()
);

-- ── 7. payment_gateway_configs ───────────────────────────────

CREATE TABLE IF NOT EXISTS payment_gateway_configs (
    id                 BIGSERIAL PRIMARY KEY,
    provider           VARCHAR(50) NOT NULL UNIQUE,
    enabled            BOOLEAN     DEFAULT FALSE,
    mode               VARCHAR(10) DEFAULT 'TEST',
    public_config_json TEXT,
    private_config_ref VARCHAR(200),
    created_at         TIMESTAMP   DEFAULT NOW(),
    updated_at         TIMESTAMP   DEFAULT NOW()
);

-- ── 8. affiliate tables ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS affiliate_rules (
    id                 BIGSERIAL PRIMARY KEY,
    reward_type        VARCHAR(20) DEFAULT 'FREE_MONTH',
    free_months        INTEGER     DEFAULT 1,
    commission_percent NUMERIC(5,2) DEFAULT 0,
    active             BOOLEAN     DEFAULT TRUE,
    created_at         TIMESTAMP   DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS affiliate_referrals (
    id                 BIGSERIAL PRIMARY KEY,
    referrer_tenant_id BIGINT      NOT NULL,
    referral_code      VARCHAR(100) NOT NULL UNIQUE,
    status             VARCHAR(20) DEFAULT 'ACTIVE',
    created_at         TIMESTAMP   DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS affiliate_conversions (
    id                    BIGSERIAL PRIMARY KEY,
    referral_id           BIGINT,
    referred_tenant_id    BIGINT NOT NULL,
    reward_type           VARCHAR(20),
    free_months_awarded   INTEGER,
    commission_amount     NUMERIC(10,2),
    converted_at          TIMESTAMP DEFAULT NOW()
);

-- ── 9. Seed default plans (idempotent) ───────────

INSERT INTO subscription_plans
    (name, code, description, monthly_price, yearly_price, currency, trial_days,
     max_vehicles, max_employees, max_gps_devices, max_reservations, client_limit, contract_limit,
     storage_limit_mb, api_access, white_label, priority_support, is_active, display_order,
     billing_cycle_allowed_monthly, billing_cycle_allowed_yearly, created_at, updated_at)
VALUES ('Trial',    'TRIAL',    'Free trial period – basic features',   0,   0,   'MAD', 14, 4,   1,  0,   100,   50,   50,   512,  FALSE, FALSE, FALSE, TRUE, 0, TRUE, FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO subscription_plans
    (name, code, description, monthly_price, yearly_price, currency, trial_days,
     max_vehicles, max_employees, max_gps_devices, max_reservations, client_limit, contract_limit,
     storage_limit_mb, api_access, white_label, priority_support, is_active, display_order,
     billing_cycle_allowed_monthly, billing_cycle_allowed_yearly, created_at, updated_at)
VALUES ('Basic',    'BASIC',    'Essential features for small fleets',  199, 1990, 'MAD', 0, 10,  2,  0,   500,   200,  200,  1024, FALSE, FALSE, FALSE, TRUE, 1, TRUE, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO subscription_plans
    (name, code, description, monthly_price, yearly_price, currency, trial_days,
     max_vehicles, max_employees, max_gps_devices, max_reservations, client_limit, contract_limit,
     storage_limit_mb, api_access, white_label, priority_support, is_active, display_order,
     billing_cycle_allowed_monthly, billing_cycle_allowed_yearly, created_at, updated_at)
VALUES ('Standard', 'STANDARD', 'Growing agencies with advanced tools', 399, 3990, 'MAD', 0, 30,  5,  5,   2000,  1000, 1000, 5120, FALSE, FALSE, FALSE, TRUE, 2, TRUE, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO subscription_plans
    (name, code, description, monthly_price, yearly_price, currency, trial_days,
     max_vehicles, max_employees, max_gps_devices, max_reservations, client_limit, contract_limit,
     storage_limit_mb, api_access, white_label, priority_support, is_active, display_order,
     billing_cycle_allowed_monthly, billing_cycle_allowed_yearly, created_at, updated_at)
VALUES ('Premium',  'PREMIUM',  'Full-featured for large operations',   799, 7990, 'MAD', 0, 200, 60, 100, 10000, 5000, 5000, 20480, TRUE,  TRUE, TRUE, TRUE, 3, TRUE, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Seed Whop payment gateway config
INSERT INTO payment_gateway_configs (provider, enabled, mode, created_at, updated_at)
SELECT 'WHOP', FALSE, 'TEST', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_gateway_configs WHERE provider = 'WHOP');
