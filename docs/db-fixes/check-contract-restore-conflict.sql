-- ============================================================
-- Contract Restore Conflict Diagnostic Queries
-- Run against your PostgreSQL database to diagnose why
-- POST /api/contracts/:id/restore returns 409.
-- Replace 111 with your actual contract id.
-- ============================================================

-- ── 1. Raw state of the contract being restored ─────────────────────
SELECT id, contract_number, contract_status, deleted, vehicle_id, reservation_id, start_date, end_date
FROM contracts
WHERE id = 111;

-- ── 2. All contracts for the same vehicle ───────────────────────────
-- Shows which contracts share the vehicle and could block restore.
SELECT id, contract_number, contract_status, deleted, vehicle_id, start_date, end_date
FROM contracts
WHERE vehicle_id = (SELECT vehicle_id FROM contracts WHERE id = 111)
ORDER BY id DESC;

-- ── 3. All reservations for the same vehicle ────────────────────────
-- Shows which reservations share the vehicle and date range.
SELECT id, status, vehicle_id,
       (SELECT id FROM contracts WHERE reservation_id = r.id LIMIT 1) AS contract_id,
       date_start AS start_date, date_end AS end_date
FROM reservations r
WHERE r.vehicle_id = (SELECT vehicle_id FROM contracts WHERE id = 111)
ORDER BY id DESC;

-- ── 4. Active conflicting contracts (exactly what the backend checks) ──
-- These rows would block restore for the date range of contract 111.
SELECT c2.id, c2.contract_number, c2.contract_status, c2.deleted,
       c2.vehicle_id, c2.start_date, c2.end_date
FROM contracts c1
JOIN contracts c2 ON c2.vehicle_id = c1.vehicle_id
WHERE c1.id = 111
  AND c2.id != 111
  AND c2.contract_status NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED')
  AND coalesce(c2.deleted, false) = false
  AND c2.start_date < c1.end_date
  AND c2.end_date > c1.start_date;

-- ── 5. Active conflicting reservations (exactly what the backend checks) ──
SELECT r.id, r.status, r.vehicle_id, r.date_start AS start_date, r.date_end AS end_date
FROM contracts c
JOIN reservations r ON r.vehicle_id = c.vehicle_id
WHERE c.id = 111
  AND r.id != coalesce(c.reservation_id, -1)
  AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED', 'CONVERTED_TO_CONTRACT')
  AND r.date_start < c.end_date
  AND r.date_end > c.start_date;

-- ── 6. Check the contract's own linked reservation ──────────────────
-- The linked reservation must be excluded from the conflict check.
SELECT r.id AS reservation_id, r.status AS reservation_status, r.date_start, r.date_end
FROM contracts c
LEFT JOIN reservations r ON r.id = c.reservation_id
WHERE c.id = 111;

-- ── 7. Verify restore window has not expired ────────────────────────
SELECT id, contract_number, deleted_at,
       (deleted_at + INTERVAL '30 days') AS restorable_until,
       NOW() AS current_time,
       CASE
           WHEN NOW() < (deleted_at + INTERVAL '30 days') THEN 'CAN RESTORE'
           ELSE 'EXPIRED'
       END AS window_status
FROM contracts
WHERE id = 111;
