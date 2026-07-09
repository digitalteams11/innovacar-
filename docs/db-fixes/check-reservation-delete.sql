-- ============================================================
-- DEBUG: reservation soft-delete state
-- ============================================================
-- Run this after deleting a reservation from the UI to verify
-- that deleted=true was set and the row is properly hidden.
-- ============================================================

-- All reservations ordered by id desc — inspect deleted column
SELECT
    id,
    status,
    deleted,
    deleted_at,
    deleted_by,
    (SELECT c.id FROM contracts c WHERE c.reservation_id = reservations.id LIMIT 1) AS contract_id,
    client_id,
    vehicle_id,
    date_start,
    date_end,
    source
FROM reservations
ORDER BY id DESC;

-- Only soft-deleted reservations (should NOT appear in /api/reservations list)
SELECT
    id,
    status,
    deleted,
    deleted_at,
    deleted_by,
    source
FROM reservations
WHERE deleted = true
ORDER BY deleted_at DESC;

-- Reservations still visible in normal list (deleted = false or null)
SELECT
    id,
    status,
    deleted,
    source
FROM reservations
WHERE deleted IS FALSE OR deleted IS NULL
ORDER BY id DESC;

-- Availability-blocking reservations (these hold vehicle dates):
-- Must NOT include deleted=true rows after V14 migration.
SELECT
    id,
    vehicle_id,
    status,
    deleted,
    date_start,
    date_end
FROM reservations
WHERE deleted IS NOT TRUE
  AND status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED', 'CONVERTED_TO_CONTRACT')
ORDER BY date_start;
