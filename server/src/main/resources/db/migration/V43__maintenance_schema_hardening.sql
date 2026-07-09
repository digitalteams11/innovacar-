-- Hardening for POST /api/maintenance on existing databases.
-- This migration is intentionally non-destructive: no rows are deleted and no tables are dropped.

CREATE TABLE IF NOT EXISTS vehicle_maintenance (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    vehicle_id BIGINT,
    title VARCHAR(150),
    description TEXT,
    service_provider VARCHAR(255),
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cost NUMERIC(12, 2),
    mileage INTEGER,
    status VARCHAR(30),
    created_by BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS vehicle_id BIGINT;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS title VARCHAR(150);
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS service_provider VARCHAR(255);
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS cost NUMERIC(12, 2);
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS mileage INTEGER;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE vehicle_maintenance ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Old maintenance schemas sometimes had branch_id as required. Branches are optional now.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'vehicle_maintenance' AND column_name = 'branch_id'
    ) THEN
        ALTER TABLE vehicle_maintenance ALTER COLUMN branch_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'maintenance_work_orders' AND column_name = 'branch_id'
    ) THEN
        ALTER TABLE maintenance_work_orders ALTER COLUMN branch_id DROP NOT NULL;
    END IF;
END $$;

-- Older databases may have created_by as text. The entity writes a User FK id, so normalize it.
DO $$
DECLARE
    created_by_type TEXT;
BEGIN
    SELECT data_type INTO created_by_type
    FROM information_schema.columns
    WHERE table_name = 'vehicle_maintenance'
      AND column_name = 'created_by';

    IF created_by_type IS NOT NULL AND created_by_type <> 'bigint' THEN
        ALTER TABLE vehicle_maintenance ALTER COLUMN created_by DROP DEFAULT;
        ALTER TABLE vehicle_maintenance
        ALTER COLUMN created_by TYPE BIGINT
        USING CASE
            WHEN created_by IS NULL THEN NULL
            WHEN created_by::TEXT ~ '^[0-9]+$' THEN created_by::TEXT::BIGINT
            ELSE NULL
        END;
    END IF;
END $$;

UPDATE vehicle_maintenance
SET status = 'SCHEDULED'
WHERE status IS NULL
   OR status NOT IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

UPDATE vehicle_maintenance SET cost = 0 WHERE cost IS NULL;
UPDATE vehicle_maintenance SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

-- Drop any old status check on vehicle_maintenance, even if the generated name differs.
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'vehicle_maintenance'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE vehicle_maintenance DROP CONSTRAINT IF EXISTS %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE vehicle_maintenance ALTER COLUMN status SET DEFAULT 'SCHEDULED';
ALTER TABLE vehicle_maintenance ALTER COLUMN status SET NOT NULL;
ALTER TABLE vehicle_maintenance ALTER COLUMN cost SET DEFAULT 0;
ALTER TABLE vehicle_maintenance ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE vehicle_maintenance ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE vehicle_maintenance
ADD CONSTRAINT vehicle_maintenance_status_check
CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

-- Creating a work order sets vehicles.statut = MAINTENANCE; make sure existing DB checks allow it.
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'vehicles'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%statut%'
    LOOP
        EXECUTE format('ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS %I', constraint_name);
    END LOOP;
END $$;

UPDATE vehicles
SET statut = 'AVAILABLE'
WHERE statut IS NULL
   OR statut NOT IN (
        'AVAILABLE', 'RESERVED', 'RENTED', 'IN_MAINTENANCE',
        'OUT_OF_SERVICE', 'SOLD', 'ARCHIVED', 'MAINTENANCE'
   );

ALTER TABLE vehicles ALTER COLUMN statut SET DEFAULT 'AVAILABLE';
ALTER TABLE vehicles
ADD CONSTRAINT vehicles_statut_check
CHECK (statut IN (
    'AVAILABLE', 'RESERVED', 'RENTED', 'IN_MAINTENANCE',
    'OUT_OF_SERVICE', 'SOLD', 'ARCHIVED', 'MAINTENANCE'
));

CREATE INDEX IF NOT EXISTS idx_maintenance_tenant_vehicle ON vehicle_maintenance (tenant_id, vehicle_id);
CREATE INDEX IF NOT EXISTS idx_maintenance_status ON vehicle_maintenance (tenant_id, status);