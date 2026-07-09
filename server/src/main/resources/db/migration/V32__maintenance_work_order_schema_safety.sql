CREATE TABLE IF NOT EXISTS vehicle_maintenance (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    service_provider VARCHAR(255),
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cost NUMERIC(12, 2) DEFAULT 0,
    mileage INTEGER,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS vehicle_id BIGINT;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS title VARCHAR(150);

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS service_provider VARCHAR(255);

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS cost NUMERIC(12, 2);

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS mileage INTEGER;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS status VARCHAR(30);

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS created_by BIGINT;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE vehicle_maintenance
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'vehicle_maintenance' AND column_name = 'branch_id'
    ) THEN
        ALTER TABLE vehicle_maintenance ALTER COLUMN branch_id DROP NOT NULL;
    END IF;
END $$;

UPDATE vehicle_maintenance
SET status = 'SCHEDULED'
WHERE status IS NULL;

UPDATE vehicle_maintenance
SET cost = 0
WHERE cost IS NULL;

UPDATE vehicle_maintenance
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

ALTER TABLE vehicle_maintenance
ALTER COLUMN status SET DEFAULT 'SCHEDULED';

ALTER TABLE vehicle_maintenance
ALTER COLUMN status SET NOT NULL;

ALTER TABLE vehicle_maintenance
ALTER COLUMN cost SET DEFAULT 0;

ALTER TABLE vehicle_maintenance
ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE vehicle_maintenance
ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_maintenance_tenant_vehicle
ON vehicle_maintenance (tenant_id, vehicle_id);

CREATE INDEX IF NOT EXISTS idx_maintenance_status
ON vehicle_maintenance (tenant_id, status);