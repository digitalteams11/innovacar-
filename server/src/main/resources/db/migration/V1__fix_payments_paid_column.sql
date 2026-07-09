-- Make payments.paid safe for existing data and future inserts.
-- This migration is intentionally non-destructive: it never drops or truncates payment data.

ALTER TABLE payments
ADD COLUMN IF NOT EXISTS paid BOOLEAN;

UPDATE payments
SET paid = CASE
    WHEN status = 'PAID' THEN TRUE
    ELSE FALSE
END
WHERE paid IS NULL;

ALTER TABLE payments
ALTER COLUMN paid SET DEFAULT FALSE;

ALTER TABLE payments
ALTER COLUMN paid SET NOT NULL;