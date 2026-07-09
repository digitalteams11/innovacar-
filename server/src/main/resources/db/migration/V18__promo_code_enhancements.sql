-- Extend promo_codes with new fields
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS first_time_only      BOOLEAN DEFAULT FALSE;
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS applies_to_all_plans BOOLEAN DEFAULT FALSE;
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS deleted               BOOLEAN DEFAULT FALSE;

-- Extend promo_code_redemptions with lifecycle status
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS status             VARCHAR(20) DEFAULT 'USED';
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS plan_code          VARCHAR(50);
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS user_id            BIGINT;
ALTER TABLE promo_code_redemptions ADD COLUMN IF NOT EXISTS whop_checkout_url  VARCHAR(500);
UPDATE promo_code_redemptions SET status = 'USED' WHERE status IS NULL;

-- Discounted Whop checkout URL per promo + plan + billing cycle (Mode 1)
CREATE TABLE IF NOT EXISTS promo_code_plan_links (
    id             BIGSERIAL    PRIMARY KEY,
    promo_code_id  BIGINT       NOT NULL,
    plan_code      VARCHAR(50)  NOT NULL,
    billing_cycle  VARCHAR(20)  NOT NULL,
    whop_checkout_url_override VARCHAR(500),
    active         BOOLEAN      DEFAULT TRUE,
    created_at     TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT uk_promo_plan_cycle UNIQUE (promo_code_id, plan_code, billing_cycle),
    CONSTRAINT fk_pcpl_promo FOREIGN KEY (promo_code_id) REFERENCES promo_codes(id)
);
