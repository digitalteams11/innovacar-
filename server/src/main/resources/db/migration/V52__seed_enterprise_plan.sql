-- Fixes the AI Assistant entitlement bug: the "Enterprise" plan is referenced
-- by DataInitializer's system tenant (Tenant.planName = 'Enterprise') and by
-- the Subscription/SuperAdminSubscriptions frontend pages, but was never
-- actually seeded into subscription_plans — only Trial/Basic/Standard/Premium
-- exist (see V7__billing_tables_and_seed_plans.sql). Any tenant whose
-- planName is 'Enterprise' therefore fails FeatureAccessService.resolveTenantPlan()
-- silently (no matching row), which disables EVERY plan-gated feature —
-- including AI_ASSISTANT — with no diagnostic trace. This migration adds the
-- missing plan and grants it the same feature set as Premium plus AI, as the
-- top platform tier.

INSERT INTO subscription_plans
    (name, code, description, monthly_price, yearly_price, currency, trial_days,
     max_vehicles, max_employees, max_gps_devices, max_reservations, client_limit, contract_limit,
     storage_limit_mb, api_access, white_label, priority_support, is_active, display_order,
     billing_cycle_allowed_monthly, billing_cycle_allowed_yearly, created_at, updated_at)
VALUES ('Enterprise', 'ENTERPRISE', 'Unlimited scale for enterprise operations.', 2499, 24990, 'MAD', 0,
        9999, 9999, 9999, 99999, 99999, 99999,
        1048576, TRUE, TRUE, TRUE, TRUE, 5, TRUE, TRUE, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

DO $$
DECLARE v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM subscription_plans WHERE code = 'ENTERPRISE';
    IF v_id IS NULL THEN RETURN; END IF;
    INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at) VALUES
        (v_id,'VEHICLE_MANAGEMENT',    TRUE,NOW(),NOW()),
        (v_id,'CLIENT_MANAGEMENT',     TRUE,NOW(),NOW()),
        (v_id,'RESERVATION_MANAGEMENT',TRUE,NOW(),NOW()),
        (v_id,'CONTRACT_MANAGEMENT',   TRUE,NOW(),NOW()),
        (v_id,'PDF_EXPORT',            TRUE,NOW(),NOW()),
        (v_id,'QR_SIGNATURE',          TRUE,NOW(),NOW()),
        (v_id,'INSPECTION_MEDIA',      TRUE,NOW(),NOW()),
        (v_id,'CUSTOM_TEMPLATES',      TRUE,NOW(),NOW()),
        (v_id,'MULTI_EMPLOYEE',        TRUE,NOW(),NOW()),
        (v_id,'MULTI_BRANCH',          TRUE,NOW(),NOW()),
        (v_id,'INVOICE_GENERATION',    TRUE,NOW(),NOW()),
        (v_id,'PAYMENTS',              TRUE,NOW(),NOW()),
        (v_id,'REPORTS_BASIC',         TRUE,NOW(),NOW()),
        (v_id,'ADVANCED_REPORTS',      TRUE,NOW(),NOW()),
        (v_id,'GPS_TRACKING',          TRUE,NOW(),NOW()),
        (v_id,'GPS_ALERTS',            TRUE,NOW(),NOW()),
        (v_id,'AI_ASSISTANT',          TRUE,NOW(),NOW()),
        (v_id,'AI_REPORTS',            TRUE,NOW(),NOW()),
        (v_id,'AI_TRANSLATIONS',       TRUE,NOW(),NOW()),
        (v_id,'WHITE_LABEL',           TRUE,NOW(),NOW()),
        (v_id,'PRIORITY_SUPPORT',      TRUE,NOW(),NOW()),
        (v_id,'API_ACCESS',            TRUE,NOW(),NOW())
    ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled=EXCLUDED.enabled, updated_at=NOW();
END $$;
