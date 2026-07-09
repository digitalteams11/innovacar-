-- Inspect the live PostgreSQL contract status constraint and existing values.
-- Run manually in psql/pgAdmin. This file is documentation only; it is not destructive.

SELECT pg_get_constraintdef(oid) AS contracts_contract_status_check_definition
FROM pg_constraint
WHERE conname = 'contracts_contract_status_check';

SELECT DISTINCT contract_status
FROM contracts
ORDER BY contract_status;

SELECT column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'contracts'
  AND column_name IN ('status', 'contract_status');
