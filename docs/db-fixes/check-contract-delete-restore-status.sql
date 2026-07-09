-- ============================================================
-- RENTCAR SAAS — Contract Delete / Restore Status Debug Queries
-- ============================================================
-- Run these manually against your PostgreSQL database to verify
-- the delete/restore/cancel logic is working correctly.
--
-- Expected after DELETE (move to trash):
--   contracts: deleted=true, contract_status=<original> (NOT 'CANCELLED'),
--              status_before_delete=<original>, previous_reservation_status=<original reservation status>
--   reservations: status='CANCELLED' (to free vehicle dates)
--   vehicles: statut='AVAILABLE' (or 'RESERVED' if another booking exists)
--
-- Expected after RESTORE NORMAL:
--   contracts: deleted=false, contract_status=<restored>, status_before_delete=null,
--              previous_reservation_status=null
--   reservations: status=<restored> (e.g. CONFIRMED)
--   vehicles: statut='RENTED' if ACTIVE, 'RESERVED' if SIGNED/PENDING etc.
--
-- Expected after CANCEL:
--   contracts: deleted=false, contract_status='CANCELLED'
--   reservations: status='CANCELLED'
--   vehicles: statut='AVAILABLE' (or 'RESERVED' if another booking)
-- ============================================================

-- 1. Contract state (all, ordered newest first)
SELECT
    id,
    contract_number,
    contract_status,
    status_before_delete          AS status_before_delete,
    previous_reservation_status,
    deleted,
    deleted_at,
    deleted_by,
    vehicle_id,
    reservation_id
FROM contracts
ORDER BY id DESC
LIMIT 30;

-- 2. Reservation state
SELECT
    id,
    reservation_number,
    status,
    vehicle_id,
    contract_id,
    start_date,
    end_date
FROM reservations
ORDER BY id DESC
LIMIT 30;

-- 3. Vehicle state
SELECT
    id,
    marque,
    statut,
    deleted
FROM vehicles
ORDER BY id;

-- 4. Contracts in trash (deleted=true, within retention window)
SELECT
    id,
    contract_number,
    contract_status,
    status_before_delete,
    previous_reservation_status,
    deleted_at,
    deleted_by
FROM contracts
WHERE deleted = true
ORDER BY deleted_at DESC;

-- 5. Cancelled (business-cancelled, NOT trashed)
SELECT
    id,
    contract_number,
    contract_status,
    deleted,
    deleted_at
FROM contracts
WHERE contract_status = 'CANCELLED'
  AND (deleted = false OR deleted IS NULL)
ORDER BY id DESC;
