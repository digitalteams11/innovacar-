-- Scheduled cancellation fields added to tenants table
-- IF NOT EXISTS: a manually-bootstrapped database (see
-- server/scripts/bootstrap/baseline-core-schema.sql) may already contain
-- these columns as part of the current entity shape, so this must be safe
-- to run against both a database that has them and one that doesn't.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMP NULL;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS cancel_effective_at TIMESTAMP NULL;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(100) NULL;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS cancellation_feedback TEXT NULL;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP NULL;
