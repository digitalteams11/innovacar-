-- ============================================================
-- V26 — Contract branding snapshot columns
-- Captures agency branding at signing time so PDF regeneration
-- always uses the branding from when the contract was signed.
-- Safe: ADD COLUMN IF NOT EXISTS
-- ============================================================

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS branding_logo_url       TEXT,
    ADD COLUMN IF NOT EXISTS branding_stamp_url      TEXT,
    ADD COLUMN IF NOT EXISTS branding_terms_snapshot TEXT;
