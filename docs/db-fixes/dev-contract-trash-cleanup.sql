-- ============================================================================
-- Contract trash — emergency local cleanup queries
-- ============================================================================
--
-- DEV ONLY. DO NOT RUN IN PRODUCTION.
--
-- The application already implements trash/restore/auto-purge for contracts
-- (see ContractService.deleteContract/restoreContract, ContractPurgeService,
-- ContractTrashCleanupJob). You should not need to run manual SQL deletes in
-- normal operation — use this file only to inspect/unblock a local dev
-- database, e.g. after manual data tinkering left orphaned or stuck rows.
--
-- Every destructive statement below is commented out by default and scoped
-- to an explicit list of contract IDs. Uncomment and fill in IDs deliberately;
-- never run a bare, unscoped DELETE.
-- ============================================================================


-- ── 1. Show all contracts currently in trash ───────────────────────────────
SELECT id, contract_number, tenant_id, contract_status, status_before_delete,
       deleted, deleted_at, deleted_by,
       deleted_at + interval '30 days' AS restorable_until
FROM contracts
WHERE deleted = true
ORDER BY deleted_at DESC;


-- ── 2. Show trash older than the retention window (purge candidates) ──────
SELECT id, contract_number, tenant_id, deleted_at
FROM contracts
WHERE deleted = true
  AND deleted_at < now() - interval '30 days'
ORDER BY deleted_at;


-- ── 3. Discover every FK table that references contracts ──────────────────
-- Run this first whenever the schema changes, to verify the purge order
-- below is still complete (e.g. if a new contract-referencing table was
-- added since this file was written).
SELECT
    conrelid::regclass AS child_table,
    conname,
    pg_get_constraintdef(oid) AS constraint_def
FROM pg_constraint
WHERE confrelid = 'contracts'::regclass
  AND contype = 'f';


-- ── 4. Discover every FK table that references inspections ────────────────
-- inspection_media must be purged before inspections.
SELECT
    conrelid::regclass AS child_table,
    conname,
    pg_get_constraintdef(oid) AS constraint_def
FROM pg_constraint
WHERE confrelid = 'inspections'::regclass
  AND contype = 'f';


-- ============================================================================
-- 5. Safe hard purge for a specific list of contract IDs (DEV ONLY)
-- ============================================================================
-- Mirrors ContractPurgeService's order: inspection_media -> inspections,
-- then payments / deposits / notifications, then the contract itself.
-- contract_audit_logs, contract_documents, contract_vehicle_conditions and
-- contract_additional_drivers cascade automatically via the JPA mapping's
-- ON DELETE behavior when the contract row is removed, but they are listed
-- explicitly here too since this script runs outside the application.
--
-- Replace (1, 2, 3) with the exact contract IDs you verified in step 1/2.
-- Wrap in a transaction so you can roll back if anything looks wrong.

-- BEGIN;
--
-- DELETE FROM inspection_media
-- WHERE inspection_id IN (SELECT id FROM inspections WHERE contract_id IN (1, 2, 3));
--
-- DELETE FROM inspections WHERE contract_id IN (1, 2, 3);
-- DELETE FROM payments WHERE contract_id IN (1, 2, 3);
-- DELETE FROM deposits WHERE contract_id IN (1, 2, 3);
-- DELETE FROM notifications WHERE contract_id IN (1, 2, 3);
-- DELETE FROM contract_audit_logs WHERE contract_id IN (1, 2, 3);
-- DELETE FROM contract_documents WHERE contract_id IN (1, 2, 3);
-- DELETE FROM contract_vehicle_conditions WHERE contract_id IN (1, 2, 3);
-- DELETE FROM contract_additional_drivers WHERE contract_id IN (1, 2, 3);
-- DELETE FROM contracts WHERE id IN (1, 2, 3);
--
-- COMMIT;

-- ============================================================================
-- WARNING: DEV ONLY. DO NOT RUN IN PRODUCTION.
-- These statements permanently destroy data with no recycle bin and no
-- undo. Never run an uncommented DELETE here against a production database.
-- Use the application's Trash / Restore / "Delete permanently" UI and the
-- automatic 30-day purge job instead.
-- ============================================================================
