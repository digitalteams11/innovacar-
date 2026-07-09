-- Safety net for POST /api/maintenance.
-- Some existing databases had already recorded earlier migrations before the
-- vehicle status constraint was widened. Re-apply the non-destructive status
-- constraint fix here so creating a maintenance work order can safely set
-- vehicles.statut = 'MAINTENANCE'.

ALTER TABLE vehicles
DROP CONSTRAINT IF EXISTS vehicles_statut_check;

UPDATE vehicles
SET statut = 'AVAILABLE'
WHERE statut IS NULL
   OR statut NOT IN (
        'AVAILABLE', 'RESERVED', 'RENTED', 'IN_MAINTENANCE',
        'OUT_OF_SERVICE', 'SOLD', 'ARCHIVED', 'MAINTENANCE'
   );

ALTER TABLE vehicles
ALTER COLUMN statut SET DEFAULT 'AVAILABLE';

ALTER TABLE vehicles
ADD CONSTRAINT vehicles_statut_check
CHECK (statut IN (
    'AVAILABLE', 'RESERVED', 'RENTED', 'IN_MAINTENANCE',
    'OUT_OF_SERVICE', 'SOLD', 'ARCHIVED', 'MAINTENANCE'
));

-- Maintenance work orders no longer require branch context.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'vehicle_maintenance'
          AND column_name = 'branch_id'
    ) THEN
        ALTER TABLE vehicle_maintenance ALTER COLUMN branch_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'maintenance_work_orders'
          AND column_name = 'branch_id'
    ) THEN
        ALTER TABLE maintenance_work_orders ALTER COLUMN branch_id DROP NOT NULL;
    END IF;
END $$;

UPDATE vehicle_maintenance
SET status = 'SCHEDULED'
WHERE status IS NULL
   OR status NOT IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

ALTER TABLE vehicle_maintenance
DROP CONSTRAINT IF EXISTS vehicle_maintenance_status_check;

ALTER TABLE vehicle_maintenance
ALTER COLUMN status SET DEFAULT 'SCHEDULED';

ALTER TABLE vehicle_maintenance
ADD CONSTRAINT vehicle_maintenance_status_check
CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));
