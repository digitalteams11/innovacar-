-- ============================================================
-- Restore as Draft Diagnostic Queries
-- Run against your PostgreSQL database when DRAFT_ONLY restore
-- is misbehaving or returning unexpected conflicts.
-- Replace 111 with your actual contract id.
-- ============================================================

-- ── 1. Raw state of the contract ────────────────────────────────────
SELECT id, contract_number, contract_status, deleted, deleted_at, deleted_by,
       vehicle_id, reservation_id, start_date, end_date
FROM contracts
WHERE id = 111;

-- ── 2. All contracts for the same vehicle ───────────────────────────
SELECT id, contract_number, contract_status, deleted, vehicle_id, start_date, end_date
FROM contracts
WHERE vehicle_id = (SELECT vehicle_id FROM contracts WHERE id = 111)
ORDER BY id DESC;

-- ── 3. All reservations for the same vehicle ────────────────────────
SELECT r.id,
       NULL AS reservation_number,
       r.status,
       r.vehicle_id,
       (SELECT c.id FROM contracts c WHERE c.reservation_id = r.id LIMIT 1) AS contract_id,
       r.date_start AS start_date,
       r.date_end   AS end_date
FROM reservations r
WHERE r.vehicle_id = (SELECT vehicle_id FROM contracts WHERE id = 111)
ORDER BY r.id DESC;

-- ── 4. Verify contract was restored as DRAFT ────────────────────────
-- After a successful DRAFT_ONLY restore, this should show
-- deleted=false, contract_status='DRAFT'.
SELECT id, contract_number, contract_status, deleted, deleted_at, vehicle_id
FROM contracts
WHERE id = 111;

-- ── 5. Confirm DRAFT contract does NOT block availability ────────────
-- After restore-as-draft, this query should return 0 rows for the
-- restored contract — DRAFT is now excluded from conflict checks.
SELECT c2.id, c2.contract_number, c2.contract_status, c2.deleted
FROM contracts c1
JOIN contracts c2 ON c2.vehicle_id = c1.vehicle_id
WHERE c1.id = 111
  AND c2.id != 111
  AND c2.contract_status NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED', 'DRAFT')
  AND coalesce(c2.deleted, false) = false
  AND c2.start_date < c1.end_date
  AND c2.end_date > c1.start_date;

-- ── 6. Verify restore window has not expired ────────────────────────
SELECT id, contract_number, deleted_at,
       (deleted_at + INTERVAL '30 days') AS restorable_until,
       NOW() AS current_time,
       CASE
           WHEN NOW() < (deleted_at + INTERVAL '30 days') THEN 'CAN RESTORE'
           ELSE 'EXPIRED — purge only'
       END AS window_status
FROM contracts
WHERE id = 111;
