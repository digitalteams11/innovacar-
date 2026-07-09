-- V40 — Dynamic deposit/guarantee fields on contracts
-- depositAmount already exists; add currency and status columns.
-- All changes are idempotent (IF NOT EXISTS).

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS deposit_currency VARCHAR(10)  DEFAULT 'MAD';

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS deposit_status  VARCHAR(50)  DEFAULT 'NOT_REQUIRED';

-- Also add new payment type values to the payments table type column
-- (no DDL needed for enum — the Java PaymentType enum is the source of truth via EnumType.STRING)
