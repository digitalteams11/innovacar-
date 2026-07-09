-- Stores the contract_status a contract had right before it was moved to
-- trash, so restoring it can put it back where it was instead of always
-- landing on a single hardcoded status. Nullable: only ever set while a
-- contract sits in trash.
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS status_before_delete VARCHAR(30);
