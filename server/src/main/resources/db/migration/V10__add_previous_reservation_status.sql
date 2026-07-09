-- Store the reservation status that was active before a contract was trashed,
-- so restore can put the reservation back to its original state instead of
-- leaving it permanently CANCELLED.
-- Safe: nullable column only, no drops, no data changes.

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS previous_reservation_status VARCHAR(50);
