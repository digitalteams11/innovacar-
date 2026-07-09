-- Stores the vehicle statut a vehicle had right before it was moved to
-- trash, so restoring it can put it back where it was (e.g. IN_MAINTENANCE)
-- instead of always forcing AVAILABLE. Nullable: only ever set while a
-- vehicle sits in trash.
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS status_before_delete VARCHAR(20);
