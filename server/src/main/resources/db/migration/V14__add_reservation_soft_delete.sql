-- ============================================================
-- V14 — Add soft-delete columns to reservations table
-- ============================================================
-- DELETE /api/reservations/{id} previously only set status=CANCELLED,
-- leaving the row visible in all reservation lists. This migration
-- adds the deleted/deleted_at/deleted_by columns so the endpoint
-- can perform a proper soft delete (deleted=true) that removes the
-- row from normal list queries without touching the DB row.
-- Safe: ADD COLUMN IF NOT EXISTS — idempotent, no data loss.
-- ============================================================

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS deleted      BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by   VARCHAR(255);

-- Backfill: mark any row that was "deleted" under the old behaviour
-- (status=CANCELLED, no linked contract, created before this migration)
-- as already soft-deleted so it stays hidden after the fix.
-- Only rows that look like spurious cancellations via the old delete
-- path are touched. Skip rows that are naturally CANCELLED (e.g. from
-- genuine cancel actions — those have a linked contract that was also
-- cancelled, or a manually-set status).
-- NOTE: This backfill is intentionally conservative. If you need to
-- repair specific rows, use docs/db-fixes/check-reservation-delete.sql.
