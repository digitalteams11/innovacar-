-- ============================================================
-- V83 — Rebrand feature_definitions.benefits copy from "RentCar" to
-- "Innovacar". V20__plan_access_control.sql is already applied in every
-- environment, so its seed text can't be edited in place (Flyway checksums
-- would break) — this migration just updates the two rows whose
-- user-facing benefits text still says "RentCar".
-- ============================================================

UPDATE feature_definitions
SET benefits = 'Remove Innovacar branding and apply your own logo, colors, and domain'
WHERE code = 'WHITE_LABEL' AND benefits LIKE '%RentCar%';

UPDATE feature_definitions
SET benefits = 'Integrate Innovacar with your own tools and workflows via REST API'
WHERE code = 'API_ACCESS' AND benefits LIKE '%RentCar%';
