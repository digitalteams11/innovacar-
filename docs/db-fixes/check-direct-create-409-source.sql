-- ============================================================
-- DIRECT-CREATE 409 SOURCE INVESTIGATION QUERIES
-- Run these queries against your PostgreSQL database to identify
-- the exact cause of the 409 DATA_CONFLICT on /api/contracts/direct-create
-- Replace the example values (CTR-2026-00002, vehicleId=11, clientId=23,
-- startDate/endDate) with actual values from the failing request.
-- ============================================================

-- 1. Check contract number duplicate (including soft-deleted rows)
-- Existence here means existsByContractNumberIncludingDeleted=true
-- → the number was already taken; backend auto-regenerates once
SELECT id, contract_number, contract_status, deleted, deleted_at,
       vehicle_id, client_id, start_date, end_date
FROM contracts
WHERE contract_number = 'CTR-2026-00002';

-- 2. Check selected vehicle
SELECT id, marque, modele, statut, deleted, tenant_id
FROM vehicles
WHERE id = 11;

-- 3. Check selected client
SELECT id, full_name, phone, cin, deleted, tenant_id
FROM clients
WHERE id = 23;

-- 4. Check ALL contracts for the selected vehicle (including deleted)
-- Look for any row with reservation_id that matches an existing reservation
SELECT id, contract_number, contract_status, deleted, deleted_at,
       vehicle_id, client_id, reservation_id, start_date, end_date
FROM contracts
WHERE vehicle_id = 11
ORDER BY id DESC;

-- 5. Check ONLY active (blocking) contracts for the selected vehicle
-- These are the contracts that would block direct-create availability
SELECT id, contract_number, contract_status, deleted, vehicle_id, start_date, end_date
FROM contracts
WHERE vehicle_id = 11
  AND COALESCE(deleted, false) = false
  AND contract_status NOT IN ('DRAFT','CANCELLED','COMPLETED','EXPIRED')
  AND start_date <= DATE '2026-07-01'
  AND end_date   >= DATE '2026-06-28'
ORDER BY id DESC;

-- 6. Check ALL reservations for the selected vehicle
-- Look for CONFIRMED reservations whose contract is deleted (source of the bug)
SELECT r.id, r.status, r.source, r.vehicle_id, r.client_id,
       r.date_start, r.date_end,
       c.id           AS contract_id,
       c.contract_number,
       c.contract_status,
       c.deleted      AS contract_deleted
FROM reservations r
LEFT JOIN contracts c ON c.reservation_id = r.id
WHERE r.vehicle_id = 11
ORDER BY r.id DESC;

-- 7. Check ONLY blocking reservations (non-cancelled, overlapping dates)
-- ROOT CAUSE: if any row appears here with contract_deleted=true,
-- that reservation is an orphaned blocker from a trashed contract.
SELECT r.id AS reservation_id, r.status, r.source,
       r.date_start, r.date_end,
       c.id           AS contract_id,
       c.contract_status,
       c.deleted      AS contract_deleted
FROM reservations r
LEFT JOIN contracts c ON c.reservation_id = r.id
WHERE r.vehicle_id = 11
  AND r.status NOT IN ('CANCELLED','EXPIRED','COMPLETED','CONVERTED_TO_CONTRACT')
  AND r.date_start <= DATE '2026-07-01'
  AND r.date_end   >= DATE '2026-06-28'
ORDER BY r.id DESC;

-- 8. Check the unique constraint that causes DATA_CONFLICT
-- If any row here has a DELETED contract (c.deleted=true), this is
-- the contracts.reservation_id unique constraint violation root cause.
SELECT c.id, c.contract_number, c.contract_status, c.deleted,
       c.reservation_id, r.status AS reservation_status, r.source
FROM contracts c
JOIN reservations r ON r.id = c.reservation_id
WHERE r.vehicle_id = 11
  AND r.date_start <= DATE '2026-07-01'
  AND r.date_end   >= DATE '2026-06-28'
ORDER BY c.id DESC;

-- 9. Verify paidAmount vs totalAmount for the failing request
-- dailyPrice=400, 4 days (Jun 28 → Jul 1), paidAmount=10000
-- Expected totalAmount = 400 * 4 = 1600
-- paidAmount=10000 > 1600 → should return PAID_AMOUNT_EXCEEDS_TOTAL
SELECT
    400::numeric                           AS daily_price,
    (DATE '2026-07-01' - DATE '2026-06-28' + 1) AS rental_days,
    400 * (DATE '2026-07-01' - DATE '2026-06-28' + 1)::numeric AS total_amount,
    10000::numeric                         AS paid_amount,
    CASE WHEN 10000 > 400 * (DATE '2026-07-01' - DATE '2026-06-28' + 1)
         THEN 'PAID_AMOUNT_EXCEEDS_TOTAL'
         ELSE 'OK'
    END AS validation_result;

-- 10. After applying the fix: verify orphaned reservations are gone
-- Should return ZERO rows after the fix for previously deleted contracts
SELECT r.id, r.status, r.source, r.date_start, r.date_end
FROM reservations r
WHERE r.vehicle_id = 11
  AND r.source = 'AUTO_FROM_CONTRACT'
  AND r.status = 'CONFIRMED'
  AND NOT EXISTS (
      SELECT 1 FROM contracts c
      WHERE c.reservation_id = r.id
        AND COALESCE(c.deleted, false) = false
  );
