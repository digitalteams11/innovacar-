-- Billing redesign, Phase 1: cancellation must be auditable, never a silent
-- status flip — who cancelled, when, and why (see ContractService#cancelContract).
-- Financial history (payments, invoices) is never deleted on cancellation;
-- these columns record the *reason* for the cancellation itself.

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cancelled_by_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
