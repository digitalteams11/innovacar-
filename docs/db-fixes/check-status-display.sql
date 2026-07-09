-- Diagnostic queries to verify vehicle and contract status consistency.
-- Run read-only against a live/dev DB to inspect current state.

SELECT id, marque, statut, deleted
FROM vehicles
WHERE COALESCE(deleted, false) = false
ORDER BY id;

SELECT id, contract_number, contract_status, deleted, vehicle_id, start_date, end_date
FROM contracts
WHERE COALESCE(deleted, false) = false
ORDER BY id DESC;

SELECT id, vehicle_id, status, start_date, end_date
FROM reservations
ORDER BY id DESC;

-- DEV ONLY: If a specific vehicle shows MAINTENANCE in the UI but the DB
-- has statut='MAINTENANCE' while it should actually be available/reserved,
-- run the query below after verifying the vehicle id from the SELECT above.
-- Uncomment and replace <vehicleId> with the real id before executing.
--
-- UPDATE vehicles
-- SET statut = 'AVAILABLE'
-- WHERE id = <vehicleId>
--   AND statut = 'MAINTENANCE';
--
-- Prefer fixing the frontend display mapping (Dashboard.tsx) if the DB
-- value is already RESERVED or AVAILABLE, rather than altering DB data.
