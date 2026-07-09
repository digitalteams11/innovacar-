ALTER TABLE inspection_media ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
UPDATE inspection_media SET active = TRUE WHERE active IS NULL;
