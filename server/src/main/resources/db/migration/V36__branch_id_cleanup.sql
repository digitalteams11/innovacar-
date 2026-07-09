-- ============================================================
-- V36 — branch_id nullable cleanup
-- Makes branch_id optional on all tables that reference it.
-- Safe: DROP NOT NULL is a no-op if the column is already nullable.
-- ============================================================

-- vehicles.branch_id — agencies without multi-branch don't set this
ALTER TABLE vehicles   ALTER COLUMN branch_id DROP NOT NULL;

-- employees may not be assigned to a branch
ALTER TABLE employees  ADD COLUMN IF NOT EXISTS branch_id BIGINT;

-- reservations may not reference a branch (booking channel may bypass)
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS branch_id BIGINT;

-- contracts: pickup/return branch FK (nullable — single-branch agencies leave this null)
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS pickup_branch_id BIGINT;
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS return_branch_id BIGINT;

-- Indexes for the new FK columns
CREATE INDEX IF NOT EXISTS idx_employee_branch    ON employees    (branch_id) WHERE branch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_reservation_branch ON reservations (branch_id) WHERE branch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_contract_pickup_branch ON contracts (pickup_branch_id) WHERE pickup_branch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_contract_return_branch ON contracts (return_branch_id) WHERE return_branch_id IS NOT NULL;
