-- ============================================================================
-- Trash state inspection — Vehicles & Contracts
-- ============================================================================
-- Read-only queries. Safe to run in any environment, including production,
-- since nothing here mutates data. Use these to confirm soft-delete/trash
-- is behaving as expected after a delete/restore/purge action.
-- ============================================================================


-- ── Vehicles ────────────────────────────────────────────────────────────────

-- All vehicles including trash
SELECT id, marque, statut, deleted, deleted_at, deleted_by, status_before_delete
FROM vehicles
ORDER BY id;

-- Active vehicles only (what GET /api/vehicles, dashboard, and the
-- contract/reservation vehicle selector all return)
SELECT id, marque, statut, deleted
FROM vehicles
WHERE COALESCE(deleted, false) = false
ORDER BY id;

-- Vehicle trash only (what GET /api/vehicles/trash returns, before the
-- 30-day-retention filter is applied)
SELECT id, marque, statut, deleted, deleted_at, deleted_by,
       deleted_at + interval '30 days' AS restorable_until
FROM vehicles
WHERE COALESCE(deleted, false) = true
ORDER BY id;

-- Vehicle trash older than retention (auto-purge candidates)
SELECT id, marque, deleted_at
FROM vehicles
WHERE COALESCE(deleted, false) = true
  AND deleted_at < now() - interval '30 days'
ORDER BY deleted_at;


-- ── Contracts ───────────────────────────────────────────────────────────────

-- All contracts including trash
SELECT id, contract_number, contract_status, deleted, deleted_at, deleted_by, status_before_delete
FROM contracts
ORDER BY id DESC;

-- Active contracts only (what GET /api/contracts, dashboard, search,
-- export, and availability conflict checks all return)
SELECT id, contract_number, contract_status, deleted
FROM contracts
WHERE COALESCE(deleted, false) = false
ORDER BY id DESC;

-- Contract trash only (what GET /api/contracts/trash returns, before the
-- 30-day-retention filter is applied)
SELECT id, contract_number, contract_status, deleted, deleted_at, deleted_by,
       deleted_at + interval '30 days' AS restorable_until
FROM contracts
WHERE COALESCE(deleted, false) = true
ORDER BY id DESC;

-- Contract trash older than retention (auto-purge candidates)
SELECT id, contract_number, deleted_at
FROM contracts
WHERE COALESCE(deleted, false) = true
  AND deleted_at < now() - interval '30 days'
ORDER BY deleted_at;


-- ── Cross-check: vehicles still blocking a permanent purge ─────────────────
-- A trashed vehicle that still appears here cannot be purged (manually or by
-- the auto-purge job) until the referencing contract/reservation is gone.
SELECT v.id AS vehicle_id, v.marque, v.deleted_at,
       (SELECT count(*) FROM contracts c WHERE c.vehicle_id = v.id) AS contract_refs,
       (SELECT count(*) FROM reservations r WHERE r.vehicle_id = v.id) AS reservation_refs
FROM vehicles v
WHERE COALESCE(v.deleted, false) = true
ORDER BY v.id;
