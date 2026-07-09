-- ============================================================
-- DEV RESET DATABASE SCRIPT
-- ============================================================
-- PURPOSE : Wipe all application data and the Flyway history
--           so that Flyway re-runs V1→V36 from scratch.
--           Useful when testing migration changes locally.
--
-- WARNING : THIS DESTROYS ALL DATA.
--           NEVER run against staging or production.
--           Double-check your DATABASE_URL before running.
--
-- HOW TO RUN (psql):
--   psql -U postgres -d location-voiture -f DEV_RESET_DATABASE.sql
--
-- After this script finishes, restart the Spring Boot app.
-- Flyway will detect an empty flyway_schema_history and
-- re-apply all migrations automatically.
-- ============================================================

-- Safety guard: block accidental run on non-dev DBs.
-- Remove or change this check if your dev DB is named differently.
DO $$
BEGIN
    IF current_database() NOT IN ('location-voiture', 'location_voiture', 'rentcar_dev', 'rentcar-dev') THEN
        RAISE EXCEPTION
            'SAFETY ABORT: current database "%" is not a recognised dev database. '
            'Edit DEV_RESET_DATABASE.sql to add it to the allowed list if you are sure.',
            current_database();
    END IF;
END $$;

-- ── Drop all application tables (reverse dependency order) ───

-- Auth / security
DROP TABLE IF EXISTS trusted_devices                CASCADE;
DROP TABLE IF EXISTS password_history               CASCADE;
DROP TABLE IF EXISTS user_sessions                  CASCADE;
DROP TABLE IF EXISTS refresh_tokens                 CASCADE;
DROP TABLE IF EXISTS email_verification_tokens      CASCADE;
DROP TABLE IF EXISTS password_reset_tokens          CASCADE;
DROP TABLE IF EXISTS phone_otps                     CASCADE;
DROP TABLE IF EXISTS login_attempts                 CASCADE;

-- Access control
DROP TABLE IF EXISTS super_admin_role_permissions   CASCADE;
DROP TABLE IF EXISTS super_admin_permission_definitions CASCADE;
DROP TABLE IF EXISTS super_admin_roles              CASCADE;
DROP TABLE IF EXISTS role_permissions               CASCADE;
DROP TABLE IF EXISTS permission_definitions         CASCADE;

-- Legal
DROP TABLE IF EXISTS legal_acceptances              CASCADE;
DROP TABLE IF EXISTS legal_documents                CASCADE;

-- Notifications / announcements
DROP TABLE IF EXISTS notification_reads             CASCADE;
DROP TABLE IF EXISTS notifications                  CASCADE;
DROP TABLE IF EXISTS announcements                  CASCADE;

-- Support
DROP TABLE IF EXISTS support_messages               CASCADE;
DROP TABLE IF EXISTS support_tickets                CASCADE;
DROP TABLE IF EXISTS knowledge_articles             CASCADE;

-- Automation
DROP TABLE IF EXISTS automation_executions          CASCADE;
DROP TABLE IF EXISTS automation_actions             CASCADE;
DROP TABLE IF EXISTS automation_rules               CASCADE;

-- GPS
DROP TABLE IF EXISTS gps_alerts                     CASCADE;
DROP TABLE IF EXISTS gps_devices                    CASCADE;
DROP TABLE IF EXISTS gps_settings                   CASCADE;

-- Inspections
DROP TABLE IF EXISTS inspection_media               CASCADE;
DROP TABLE IF EXISTS inspections                    CASCADE;

-- Contract sub-tables
DROP TABLE IF EXISTS contract_audit_logs            CASCADE;
DROP TABLE IF EXISTS contract_documents             CASCADE;
DROP TABLE IF EXISTS contract_vehicle_conditions    CASCADE;
DROP TABLE IF EXISTS contract_additional_drivers    CASCADE;
DROP TABLE IF EXISTS contract_template_fields       CASCADE;
DROP TABLE IF EXISTS contract_templates             CASCADE;
DROP TABLE IF EXISTS contract_terms                 CASCADE;

-- Email
DROP TABLE IF EXISTS email_logs                     CASCADE;
DROP TABLE IF EXISTS email_templates                CASCADE;

-- Finance / billing
DROP TABLE IF EXISTS promo_code_redemptions         CASCADE;
DROP TABLE IF EXISTS promo_code_plan_links          CASCADE;
DROP TABLE IF EXISTS promo_codes                    CASCADE;
DROP TABLE IF EXISTS deposits                       CASCADE;
DROP TABLE IF EXISTS payments                       CASCADE;
DROP TABLE IF EXISTS invoices                       CASCADE;
DROP TABLE IF EXISTS subscription_invoices          CASCADE;
DROP TABLE IF EXISTS subscription_events            CASCADE;
DROP TABLE IF EXISTS payment_gateway_configs        CASCADE;
DROP TABLE IF EXISTS agency_balance_transactions    CASCADE;
DROP TABLE IF EXISTS cancellation_requests          CASCADE;

-- Affiliate
DROP TABLE IF EXISTS affiliate_conversions          CASCADE;
DROP TABLE IF EXISTS affiliate_referrals            CASCADE;
DROP TABLE IF EXISTS affiliate_rules                CASCADE;

-- AI
DROP TABLE IF EXISTS ai_audit_logs                  CASCADE;
DROP TABLE IF EXISTS ai_settings                    CASCADE;

-- Audit / data management
DROP TABLE IF EXISTS audit_logs                     CASCADE;
DROP TABLE IF EXISTS data_reset_audit_logs          CASCADE;
DROP TABLE IF EXISTS backup_records                 CASCADE;

-- Core business entities
DROP TABLE IF EXISTS vehicle_maintenance            CASCADE;
DROP TABLE IF EXISTS contracts                      CASCADE;
DROP TABLE IF EXISTS reservations                   CASCADE;
DROP TABLE IF EXISTS employees                      CASCADE;
DROP TABLE IF EXISTS vehicles                       CASCADE;
DROP TABLE IF EXISTS clients                        CASCADE;

-- Plan / feature system
DROP TABLE IF EXISTS tenant_feature_overrides       CASCADE;
DROP TABLE IF EXISTS plan_features                  CASCADE;
DROP TABLE IF EXISTS feature_definitions            CASCADE;
DROP TABLE IF EXISTS subscription_plans             CASCADE;

-- Settings / config
DROP TABLE IF EXISTS white_label_settings           CASCADE;
DROP TABLE IF EXISTS tenant_settings                CASCADE;
DROP TABLE IF EXISTS platform_settings              CASCADE;
DROP TABLE IF EXISTS onboarding_progress            CASCADE;

-- Core tenant entities
DROP TABLE IF EXISTS branches                       CASCADE;
DROP TABLE IF EXISTS users                          CASCADE;
DROP TABLE IF EXISTS tenants                        CASCADE;

-- Flyway history — causes Flyway to re-run all migrations on next boot
DROP TABLE IF EXISTS flyway_schema_history          CASCADE;

-- ── Sequences (optional — will be recreated by migrations) ───
-- These are dropped automatically via CASCADE above for BIGSERIAL columns.

-- ── Confirmation ─────────────────────────────────────────────
DO $$
BEGIN
    RAISE NOTICE '===============================================';
    RAISE NOTICE ' DEV RESET COMPLETE on database: %', current_database();
    RAISE NOTICE ' Restart Spring Boot to re-apply all migrations.';
    RAISE NOTICE '===============================================';
END $$;
