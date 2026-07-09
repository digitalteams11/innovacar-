-- ============================================================
-- Contract Trash / Restore Diagnostic Queries
-- Run against your PostgreSQL database to diagnose lifecycle
-- issues for a specific contract or across all trashed contracts.
-- Replace :contractId and :tenantId with actual values.
-- ============================================================

-- ── 1. Inspect the raw state of a specific contract ─────────────────
-- Bypasses soft-delete filter to show the real row in the DB.
SELECT
    id,
    contract_number,
    contract_status,
    deleted,
    deleted_at,
    deleted_by,
    status_before_delete,
    vehicle_id,
    start_date,
    end_date,
    pickup_time,
    return_time,
    tenant_id
FROM contracts
WHERE id = :contractId;

-- ── 2. List all trashed contracts for a tenant ──────────────────────
SELECT
    id,
    contract_number,
    contract_status,
    deleted_at,
    deleted_by,
    status_before_delete,
    vehicle_id,
    start_date,
    end_date,
    tenant_id,
    (deleted_at + INTERVAL '30 days') AS restorable_until,
    CASE
        WHEN NOW() < (deleted_at + INTERVAL '30 days') THEN 'can_restore'
        ELSE 'expired'
    END AS restore_status
FROM contracts
WHERE tenant_id = :tenantId
  AND coalesce(deleted, false) = true
ORDER BY deleted_at DESC;

-- ── 3. Find what's blocking a restore (active conflicts for the vehicle) ──
-- Replace :vehicleId, :startDate, :endDate with values from the trashed contract.
SELECT
    id,
    contract_number,
    contract_status,
    vehicle_id,
    start_date,
    end_date,
    pickup_time,
    return_time,
    deleted,
    tenant_id
FROM contracts
WHERE tenant_id = :tenantId
  AND vehicle_id = :vehicleId
  AND id != :contractId
  AND contract_status NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED')
  AND coalesce(deleted, false) = false
  AND start_date < :endDate
  AND end_date > :startDate;

-- ── 4. Find blocking reservations for the same vehicle and dates ────
SELECT
    r.id,
    r.status,
    r.date_start,
    r.date_end,
    r.vehicle_id,
    r.tenant_id
FROM reservations r
WHERE r.tenant_id = :tenantId
  AND r.vehicle_id = :vehicleId
  AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED', 'CONVERTED_TO_CONTRACT')
  AND r.date_start < :endDate
  AND r.date_end > :startDate;

-- ── 5. Check linked reservation for a specific contract ─────────────
SELECT
    r.id AS reservation_id,
    r.status AS reservation_status,
    r.date_start,
    r.date_end,
    c.id AS contract_id,
    c.contract_number
FROM reservations r
JOIN contracts c ON c.reservation_id = r.id
WHERE c.id = :contractId;

-- ── 6. Check Flyway migration history for any failed V7 entry ───────
SELECT
    version,
    description,
    success,
    installed_on,
    execution_time
FROM flyway_schema_history
WHERE version = '7'
ORDER BY installed_on DESC;

-- ── 7. Verify subscription_plans seeded correctly (no duplicates) ───
SELECT id, name, code, is_active, created_at
FROM subscription_plans
ORDER BY display_order;

-- ── 8. Check for contracts with missing statusBeforeDelete ──────────
-- These may fail to restore to a sensible status.
SELECT
    id,
    contract_number,
    contract_status,
    status_before_delete,
    deleted_at,
    tenant_id
FROM contracts
WHERE coalesce(deleted, false) = true
  AND status_before_delete IS NULL;

-- ── 9. Manually fix missing statusBeforeDelete (if needed) ──────────
-- Uncomment and run ONLY if query 8 returns rows you want to recover.
-- UPDATE contracts
-- SET status_before_delete = 'WAITING_SIGNATURE'
-- WHERE coalesce(deleted, false) = true
--   AND status_before_delete IS NULL
--   AND tenant_id = :tenantId;

-- ── 10. Purge a specific trashed contract manually (emergency) ───────
-- Uncomment block only after verifying the contract is truly trash.
-- DELETE FROM payments          WHERE contract_id = :contractId;
-- DELETE FROM deposits          WHERE contract_id = :contractId;
-- DELETE FROM inspections       WHERE contract_id = :contractId;
-- DELETE FROM notifications     WHERE contract_id = :contractId;
-- DELETE FROM contracts         WHERE id = :contractId AND coalesce(deleted, false) = true;
