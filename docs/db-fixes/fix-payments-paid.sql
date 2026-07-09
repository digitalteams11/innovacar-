-- Fix existing and future rows for payments.paid without deleting data.
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