-- ============================================================
-- V85 — Correct the TRIAL plan's limits to match the current product spec:
-- 14-day trial, max 5 vehicles, max 20 contracts, max 2 employees, no GPS,
-- no AI, no advanced reports.
--
-- trial_days was already 14 (V7) and GPS_TRACKING/AI_ASSISTANT/ADVANCED_REPORTS
-- were already disabled for TRIAL in plan_features (V20/V53/V79) — only the
-- numeric seat/resource caps needed correcting (max_vehicles was 4, not 5;
-- max_employees was 1, not 2; contract_limit was 50, not 20). Deliberately
-- scoped to the TRIAL plan row only — Basic/Premium limits are untouched.
-- ============================================================

UPDATE subscription_plans
SET max_vehicles = 5,
    max_employees = 2,
    contract_limit = 20,
    updated_at = NOW()
WHERE UPPER(code) = 'TRIAL';
