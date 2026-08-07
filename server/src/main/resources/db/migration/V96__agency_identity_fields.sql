-- ============================================================
-- V92 — Agency Profile identity fields.
-- Adds the remaining identity fields Settings > Agency Profile needs
-- (Moroccan business identifiers ICE/IF/RC, legal name distinct from the
-- agency's display name, WhatsApp contact, and the named representative who
-- signs on the agency's behalf) directly onto tenants — mirrors the existing
-- flat-column convention already used for tax_id/address/agency_signature
-- etc, no new table.
-- ============================================================

ALTER TABLE tenants
    ADD COLUMN legal_name VARCHAR(255),
    ADD COLUMN whatsapp VARCHAR(30),
    ADD COLUMN ice VARCHAR(50),
    ADD COLUMN if_number VARCHAR(50),
    ADD COLUMN rc_number VARCHAR(50),
    ADD COLUMN representative_name VARCHAR(150);
