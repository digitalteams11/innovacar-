-- Fix: POST /api/contracts/direct-create failed with 409 DATA_CONFLICT,
-- constraint vehicles_statut_check, when ContractService.buildAndPersistContract
-- set vehicle.statut = RESERVED.
--
-- Root cause: vehicles_statut_check only allowed ('AVAILABLE', 'RENTED',
-- 'MAINTENANCE') -- a stale constraint that predates the VehicleStatus enum
-- being extended with RESERVED, IN_MAINTENANCE, OUT_OF_SERVICE, SOLD, ARCHIVED.
-- Code across ContractService, ReservationService, AvailabilityService, and
-- VehicleMaintenanceService has assigned these additional values for some
-- time; the DB constraint was simply never updated to match.
--
-- This migration is non-destructive: it never drops the vehicles table and
-- never removes the check constraint permanently -- it replaces it with one
-- that matches every value the Java VehicleStatus enum actually defines.

ALTER TABLE vehicles
DROP CONSTRAINT IF EXISTS vehicles_statut_check;

-- Defensive only: at the time of writing, all rows are already 'AVAILABLE',
-- so this normalizes nothing today, but protects any future stray/legacy
-- value (or NULL) from re-breaking this constraint.
UPDATE vehicles
SET statut = 'AVAILABLE'
WHERE statut IS NULL
   OR statut NOT IN (
        'AVAILABLE', 'RESERVED', 'RENTED', 'IN_MAINTENANCE',
        'OUT_OF_SERVICE', 'SOLD', 'ARCHIVED', 'MAINTENANCE'
   );

ALTER TABLE vehicles
ADD CONSTRAINT vehicles_statut_check
CHECK (statut IN (
    'AVAILABLE', 'RESERVED', 'RENTED', 'IN_MAINTENANCE',
    'OUT_OF_SERVICE', 'SOLD', 'ARCHIVED', 'MAINTENANCE'
));
