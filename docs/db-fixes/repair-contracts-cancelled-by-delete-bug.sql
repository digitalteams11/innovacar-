-- ============================================================
-- REPAIR: contracts incorrectly marked CANCELLED by delete bug
-- ============================================================
-- Root cause: an older version of ContractService.deleteContract()
-- called contract.setStatus(ContractStatus.CANCELLED) before the
-- soft-delete path was separated from the cancel path. The bug was
-- fixed in code; this script repairs any rows that were affected
-- before the fix was deployed.
--
-- SAFETY RULES:
--   • Only repair rows that are TRASHED (deleted = true) AND
--     have contract_status = CANCELLED — these are the ones that
--     were incorrectly changed by the delete action.
--   • Do NOT touch genuinely-cancelled contracts (deleted = false,
--     contract_status = CANCELLED) — those were cancelled by the
--     explicit cancel action and must stay CANCELLED.
--   • Dry-run (SELECT) first, then apply (UPDATE) only after
--     confirming the affected row count is expected.
--   • Adjust the id list (or remove the id filter) as needed.
-- ============================================================

-- ── STEP 1: Inspect affected rows ──────────────────────────────

SELECT
    id,
    contract_number,
    contract_status,
    status_before_delete,
    deleted,
    deleted_at,
    deleted_by
FROM contracts
WHERE deleted = true
  AND contract_status = 'CANCELLED'
  -- Narrow to known bad rows first; remove the id filter to see all affected rows.
  AND id IN (2, 3)
ORDER BY id;

-- ── STEP 2: Repair — prefer status_before_delete if it exists ──

-- If status_before_delete is populated (V3+ migration), use it:
UPDATE contracts
SET contract_status = status_before_delete
WHERE id IN (2, 3)
  AND deleted = true
  AND contract_status = 'CANCELLED'
  AND status_before_delete IS NOT NULL
  AND status_before_delete <> 'CANCELLED';

-- If status_before_delete is NULL (trashed before V3 migration), fall back to ACTIVE:
UPDATE contracts
SET contract_status = 'ACTIVE'
WHERE id IN (2, 3)
  AND deleted = true
  AND contract_status = 'CANCELLED'
  AND (status_before_delete IS NULL OR status_before_delete = 'CANCELLED');

-- ── STEP 3: Also repair previously-cancelled reservations ───────

-- Old delete code also set linked reservation.status = 'CANCELLED'.
-- Restore these too if the contract's previous_reservation_status was saved.
UPDATE reservations r
SET status = c.previous_reservation_status
FROM contracts c
WHERE c.reservation_id = r.id
  AND c.id IN (2, 3)
  AND c.deleted = true
  AND r.status = 'CANCELLED'
  AND c.previous_reservation_status IS NOT NULL
  AND c.previous_reservation_status <> 'CANCELLED';

-- If previous_reservation_status was not saved, restore reservation to CONFIRMED:
UPDATE reservations r
SET status = 'CONFIRMED'
FROM contracts c
WHERE c.reservation_id = r.id
  AND c.id IN (2, 3)
  AND c.deleted = true
  AND r.status = 'CANCELLED'
  AND (c.previous_reservation_status IS NULL OR c.previous_reservation_status = 'CANCELLED');

-- ── STEP 4: Verify ─────────────────────────────────────────────

SELECT
    c.id,
    c.contract_number,
    c.contract_status,
    c.status_before_delete,
    c.deleted,
    c.previous_reservation_status,
    r.status AS reservation_status
FROM contracts c
LEFT JOIN reservations r ON c.reservation_id = r.id
WHERE c.id IN (2, 3);

-- ── SCOPE FOR ALL-ENV REPAIR (remove id filter) ────────────────
-- If you need to repair ALL affected rows across the database,
-- remove "AND id IN (2, 3)" from every statement above.
-- Be conservative: run the SELECT first to verify the count.
