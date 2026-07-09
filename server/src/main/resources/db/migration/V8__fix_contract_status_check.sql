-- Align contracts.contract_status with the Java ContractStatus enum.
-- Safe migration: does not delete data, does not drop the contracts table.

ALTER TABLE contracts
DROP CONSTRAINT IF EXISTS contracts_contract_status_check;

UPDATE contracts
SET contract_status = 'PENDING_SIGNATURE'
WHERE contract_status IS NULL
   OR contract_status NOT IN (
        'DRAFT',
        'WAITING_SIGNATURE',
        'WAITING_CLIENT_SIGNATURE',
        'PENDING_SIGNATURE',
        'PARTIALLY_SIGNED',
        'SIGNED',
        'ACTIVE',
        'PAID',
        'COMPLETED',
        'CANCELLED',
        'EXPIRED'
   );

ALTER TABLE contracts
ALTER COLUMN contract_status SET DEFAULT 'PENDING_SIGNATURE';

ALTER TABLE contracts
ALTER COLUMN contract_status SET NOT NULL;

ALTER TABLE contracts
ADD CONSTRAINT contracts_contract_status_check
CHECK (contract_status IN (
    'DRAFT',
    'WAITING_SIGNATURE',
    'WAITING_CLIENT_SIGNATURE',
    'PENDING_SIGNATURE',
    'PARTIALLY_SIGNED',
    'SIGNED',
    'ACTIVE',
    'PAID',
    'COMPLETED',
    'CANCELLED',
    'EXPIRED'
));
