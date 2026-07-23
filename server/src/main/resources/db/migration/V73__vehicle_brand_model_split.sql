-- Splits the combined "marque" field (e.g. "Toyota Corolla 2023") into
-- separate brand/model columns so fleet exports can show them as two
-- distinct columns instead of one concatenated string. marque is kept
-- (not dropped) as the source of truth for any code not yet migrated.

ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS model VARCHAR(100);

-- Best-effort backfill: first word becomes brand, remainder becomes model.
-- Single-word marque values are left with a null model for manual cleanup.
UPDATE vehicles
SET brand = split_part(btrim(marque), ' ', 1),
    model = NULLIF(btrim(substring(btrim(marque) from length(split_part(btrim(marque), ' ', 1)) + 1)), '')
WHERE marque IS NOT NULL AND btrim(marque) <> '' AND brand IS NULL;
