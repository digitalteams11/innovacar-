-- ============================================================
-- V53 — Repair missing plan_features rows for AI_ASSISTANT and
-- other feature codes introduced by V20__plan_access_control.sql.
--
-- Root cause: this environment's plan_features table was seeded by an
-- earlier version of the feature catalog (legacy codes such as
-- SUPPORT_PRIORITY, REPORTS_ADVANCED, EXPORT_PDF, CUSTOM_CONTRACT_TEMPLATES
-- — no GPS_ALERTS, no AI_ASSISTANT, no AI_REPORTS). V20 was already recorded
-- as applied in flyway_schema_history by the time its content was rewritten
-- to the current canonical code set, so Flyway never re-ran it and the
-- newer codes (most importantly AI_ASSISTANT) were never inserted for ANY
-- plan — not even Premium, which real active tenants are on. Every
-- plan-gated check for AI_ASSISTANT therefore resolved to "no matching
-- enabled row" => always blocked, regardless of the tenant's actual plan.
--
-- This migration is a forward-only repair (never edit an already-applied
-- migration again — see the incident above): it (re)inserts the rows V20
-- intended, matched case-insensitively against subscription_plans.code so
-- it's resilient to the 'TRIAL' vs 'trial' casing drift that caused this.
-- Idempotent via ON CONFLICT DO UPDATE.
-- ============================================================

DO $$
DECLARE
    rec RECORD;
    v_enabled BOOLEAN;
BEGIN
    FOR rec IN SELECT id, code FROM subscription_plans WHERE code IS NOT NULL AND code <> '' LOOP
        -- AI_ASSISTANT / AI_REPORTS: Premium and Enterprise only (matches V20 / V52 intent)
        v_enabled := UPPER(rec.code) IN ('PREMIUM', 'ENTERPRISE');
        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (rec.id, 'AI_ASSISTANT', v_enabled, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (rec.id, 'AI_REPORTS', v_enabled, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

        -- GPS_ALERTS: Standard, Premium, Enterprise (matches V20 intent)
        v_enabled := UPPER(rec.code) IN ('STANDARD', 'PREMIUM', 'ENTERPRISE');
        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (rec.id, 'GPS_ALERTS', v_enabled, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

        -- QR_SIGNATURE / INSPECTION_MEDIA / CUSTOM_TEMPLATES / MULTI_BRANCH /
        -- ADVANCED_REPORTS: Standard, Premium, Enterprise
        v_enabled := UPPER(rec.code) IN ('STANDARD', 'PREMIUM', 'ENTERPRISE');
        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (rec.id, 'CUSTOM_TEMPLATES', v_enabled, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (rec.id, 'ADVANCED_REPORTS', v_enabled, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

        -- PDF_EXPORT: every plan (matches V20 intent — TRUE across the board)
        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (rec.id, 'PDF_EXPORT', TRUE, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

        -- PRIORITY_SUPPORT / WHITE_LABEL / API_ACCESS: Premium, Enterprise only
        v_enabled := UPPER(rec.code) IN ('PREMIUM', 'ENTERPRISE');
        INSERT INTO plan_features (plan_id, feature_code, enabled, created_at, updated_at)
        VALUES (rec.id, 'PRIORITY_SUPPORT', v_enabled, NOW(), NOW())
        ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();
    END LOOP;
END $$;
