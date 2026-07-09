-- Scheduled cancellation fields added to tenants table
ALTER TABLE tenants ADD COLUMN cancel_requested_at TIMESTAMP NULL;
ALTER TABLE tenants ADD COLUMN cancel_effective_at TIMESTAMP NULL;
ALTER TABLE tenants ADD COLUMN cancellation_reason VARCHAR(100) NULL;
ALTER TABLE tenants ADD COLUMN cancellation_feedback TEXT NULL;
ALTER TABLE tenants ADD COLUMN cancelled_at TIMESTAMP NULL;
