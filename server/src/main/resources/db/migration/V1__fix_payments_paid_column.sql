-- Make payments.paid safe for existing data and future inserts.
-- This migration is intentionally non-destructive: it never drops or truncates payment data.
-- Some local/dev databases start empty and create the base schema outside Flyway, so only
-- apply this patch when the payments table already exists.

DO $$
BEGIN
    IF to_regclass('public.payments') IS NOT NULL THEN
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
    END IF;
END $$;
