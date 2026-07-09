-- V9: Add vehicle condition tracking fields and contract condition notes
-- All columns are nullable to preserve existing data

ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS fuel_level_current    VARCHAR(30),
    ADD COLUMN IF NOT EXISTS mileage_current       INTEGER,
    ADD COLUMN IF NOT EXISTS license_expiry_date   DATE,
    ADD COLUMN IF NOT EXISTS circulation_authorization_expiry_date DATE,
    ADD COLUMN IF NOT EXISTS condition_status      VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_inspection_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_returned_at      TIMESTAMP;

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS condition_start_note  TEXT,
    ADD COLUMN IF NOT EXISTS condition_end_note    TEXT,
    ADD COLUMN IF NOT EXISTS damage_start_note     TEXT,
    ADD COLUMN IF NOT EXISTS damage_end_note       TEXT;
