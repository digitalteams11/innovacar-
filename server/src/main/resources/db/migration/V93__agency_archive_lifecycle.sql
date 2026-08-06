-- ============================================================
-- V93 — Agency archive lifecycle.
-- Adds a safe, auditable "archive" state for Super Admin agency management,
-- distinct from both the billing-lifecycle `status` (SubscriptionStatus) and
-- the manual `account_state` (BLOCKED/INACTIVE) already on tenants. Archiving
-- blocks access without touching a single business record — the opposite of
-- the previous unsafe flow, which called a raw DELETE straight into
-- foreign-key-constrained tenant data.
-- ============================================================

ALTER TABLE tenants
    ADD COLUMN archived_at TIMESTAMP,
    ADD COLUMN archived_by VARCHAR(255),
    ADD COLUMN archive_reason VARCHAR(500);
