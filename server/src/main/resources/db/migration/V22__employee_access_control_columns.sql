ALTER TABLE users
ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN;

UPDATE users
SET must_change_password = FALSE
WHERE must_change_password IS NULL;

ALTER TABLE users
ALTER COLUMN must_change_password SET DEFAULT FALSE;

ALTER TABLE users
ALTER COLUMN must_change_password SET NOT NULL;

ALTER TABLE employees
ADD COLUMN IF NOT EXISTS deleted BOOLEAN;

UPDATE employees
SET deleted = FALSE
WHERE deleted IS NULL;

ALTER TABLE employees
ALTER COLUMN deleted SET DEFAULT FALSE;

ALTER TABLE employees
ALTER COLUMN deleted SET NOT NULL;

ALTER TABLE employees
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE reservations
ADD COLUMN IF NOT EXISTS assigned_employee_id BIGINT;

ALTER TABLE contracts
ADD COLUMN IF NOT EXISTS assigned_employee_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_reservations_assigned_employee
ON reservations (assigned_employee_id);

CREATE INDEX IF NOT EXISTS idx_contracts_assigned_employee
ON contracts (assigned_employee_id);