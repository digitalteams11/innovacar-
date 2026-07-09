-- ============================================================
-- CONTRACT PURGE FK DEBUG
-- Run this against the live DB to find ALL tables that have
-- a FK referencing contracts.id.  Any table listed here will
-- block DELETE FROM contracts WHERE id = ? if rows still exist.
-- ============================================================

SELECT
    tc.table_name         AS child_table,
    kcu.column_name       AS child_column,
    ccu.table_name        AS parent_table,
    ccu.column_name       AS parent_column,
    tc.constraint_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema   = kcu.table_schema
JOIN information_schema.constraint_column_usage ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema   = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND ccu.table_name     = 'contracts'
  AND ccu.column_name    = 'id'
ORDER BY tc.table_name;

-- ============================================================
-- EXPECTED CHILD TABLES (as of 2026-06)
-- ============================================================
-- child_table                   | child_column
-- ------------------------------|------------------
-- payments                      | contract_id
-- deposits                      | contract_id
-- contract_additional_drivers   | contract_id
-- contract_vehicle_conditions   | contract_id
-- contract_documents            | contract_id
-- contract_audit_logs           | contract_id
-- inspections                   | contract_id
-- inspection_media              | (via inspections.id)
--
-- NOTE: notifications.contract_id is a plain Long column,
--       NOT a FK constraint — it will NOT block contract delete.
-- ============================================================

-- ============================================================
-- VERIFY: Check remaining child rows for a specific contract
-- Replace :contractId with the actual contract ID to inspect.
-- ============================================================

-- :contractId = 2  (example from 409 report)

SELECT 'payments'                    AS tbl, COUNT(*) AS rows FROM payments                    WHERE contract_id = 2
UNION ALL
SELECT 'deposits'                    AS tbl, COUNT(*) AS rows FROM deposits                    WHERE contract_id = 2
UNION ALL
SELECT 'contract_additional_drivers' AS tbl, COUNT(*) AS rows FROM contract_additional_drivers WHERE contract_id = 2
UNION ALL
SELECT 'contract_vehicle_conditions' AS tbl, COUNT(*) AS rows FROM contract_vehicle_conditions WHERE contract_id = 2
UNION ALL
SELECT 'contract_documents'          AS tbl, COUNT(*) AS rows FROM contract_documents          WHERE contract_id = 2
UNION ALL
SELECT 'contract_audit_logs'         AS tbl, COUNT(*) AS rows FROM contract_audit_logs         WHERE contract_id = 2
UNION ALL
SELECT 'inspections'                 AS tbl, COUNT(*) AS rows FROM inspections                 WHERE contract_id = 2
UNION ALL
SELECT 'inspection_media'            AS tbl, COUNT(*) AS rows FROM inspection_media
    WHERE inspection_id IN (SELECT id FROM inspections WHERE contract_id = 2)
UNION ALL
SELECT 'notifications(no FK)'        AS tbl, COUNT(*) AS rows FROM notifications               WHERE contract_id = 2;

-- ============================================================
-- VERIFY: Confirm contract row is gone after purge
-- ============================================================
-- SELECT * FROM contracts WHERE id = 2;          -- expect 0 rows
-- SELECT id, marque, statut FROM vehicles;       -- vehicle must still exist
-- SELECT id, full_name FROM clients;             -- client must still exist
-- ============================================================
