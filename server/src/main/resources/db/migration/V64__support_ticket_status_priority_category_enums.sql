-- Normalize status: anything not in the known set becomes UNKNOWN (idempotent).
UPDATE support_tickets
SET status = 'UNKNOWN'
WHERE status IS NULL OR status NOT IN ('OPEN','IN_PROGRESS','WAITING','RESOLVED','CLOSED','UNKNOWN');

-- Normalize priority: the known bug source is "NORMAL" (PublicContactController used it
-- before this PR, though it never matched the documented LOW/MEDIUM/HIGH/CRITICAL set) —
-- map it to MEDIUM specifically since it clearly meant "default", not garbage data.
UPDATE support_tickets SET priority = 'MEDIUM' WHERE priority = 'NORMAL';
UPDATE support_tickets
SET priority = 'UNKNOWN'
WHERE priority IS NULL OR priority NOT IN ('LOW','MEDIUM','HIGH','CRITICAL','UNKNOWN');

-- Normalize category: legacy complaint rows were encoded as category = 'COMPLAINT_' + category
-- by OperationsCenterController. Strip the prefix back to the base category where recognizable,
-- since this PR moves complaint routing to the (unchanged, raw-String) channel field instead.
UPDATE support_tickets
SET category = SUBSTRING(category FROM 11)
WHERE category LIKE 'COMPLAINT\_%'
  AND SUBSTRING(category FROM 11) IN ('BILLING','TECHNICAL','GPS','ACCOUNT','FEATURE_REQUEST','OTHER');

UPDATE support_tickets
SET channel = 'COMPLAINT'
WHERE category LIKE 'COMPLAINT\_%' AND (channel IS NULL OR channel <> 'COMPLAINT');

UPDATE support_tickets
SET category = 'UNKNOWN'
WHERE category IS NOT NULL
  AND category NOT IN ('BILLING','TECHNICAL','GPS','ACCOUNT','FEATURE_REQUEST','OTHER','UNKNOWN');

-- Enforce NOT NULL now that status/priority are guaranteed populated.
ALTER TABLE support_tickets ALTER COLUMN status SET NOT NULL;
ALTER TABLE support_tickets ALTER COLUMN priority SET NOT NULL;
