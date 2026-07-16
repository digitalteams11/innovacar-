-- platform_settings is meant to be a singleton table, but earlier application code read/wrote
-- it via an unordered "first row" lookup and lazily inserted a new row from several independent
-- endpoints. Under concurrent first-load requests this could create more than one row, after
-- which SMTP save/read/test/diagnose endpoints could silently bind to different rows (symptom:
-- "SMTP host not set" even right after saving). This is a one-time idempotent repair: keep the
-- row most likely to hold real configuration (prefer one with smtp_host set, else the oldest
-- row) and delete any extras. No schema/constraint change — the application now guards against
-- future duplicates via PlatformSettingsRepository.getOrCreateSingleton().
DO $$
DECLARE
    keep_id BIGINT;
BEGIN
    SELECT id INTO keep_id
    FROM platform_settings
    ORDER BY (smtp_host IS NOT NULL AND smtp_host <> '') DESC, id ASC
    LIMIT 1;

    IF keep_id IS NOT NULL THEN
        DELETE FROM platform_settings WHERE id <> keep_id;
    END IF;
END $$;
