-- Manual, admin-entered seat count per vehicle. Nullable and left unset for
-- all existing rows on purpose — there is no reliable way to infer a real
-- seat count from category/model, so legacy vehicles stay null until an
-- admin edits them (spec: never backfill/default to 5).
ALTER TABLE vehicles ADD COLUMN seat_count INTEGER;
