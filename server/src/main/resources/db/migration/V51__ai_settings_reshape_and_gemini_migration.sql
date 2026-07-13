-- Reshape ai_settings into a global-only config row and migrate the legacy
-- singleton Gemini config into the new ai_providers/ai_models tables.
-- No data is dropped: the encrypted key and model names are copied forward
-- before their source columns are removed, and ai_audit_logs is renamed
-- (not dropped) so historical rows remain readable.

ALTER TABLE ai_settings
    ADD COLUMN IF NOT EXISTS active_provider_id     BIGINT,
    ADD COLUMN IF NOT EXISTS active_model_id         BIGINT,
    ADD COLUMN IF NOT EXISTS fallback_provider_id    BIGINT,
    ADD COLUMN IF NOT EXISTS fallback_model_id       BIGINT,
    ADD COLUMN IF NOT EXISTS fallback_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS global_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS max_output_tokens       INTEGER,
    ADD COLUMN IF NOT EXISTS request_timeout_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS max_retries             INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS system_prompt           TEXT,
    ADD COLUMN IF NOT EXISTS updated_by              BIGINT;

UPDATE ai_settings SET global_enabled = COALESCE(enabled, FALSE);
UPDATE ai_settings SET max_output_tokens = max_tokens WHERE max_output_tokens IS NULL;
UPDATE ai_settings SET request_timeout_seconds = timeout_seconds WHERE request_timeout_seconds IS NULL;

DO $$
DECLARE
    legacy RECORD;
    new_provider_id BIGINT;
    key_was_present BOOLEAN;
BEGIN
    SELECT * INTO legacy FROM ai_settings ORDER BY id LIMIT 1;

    IF legacy IS NULL THEN
        RETURN;
    END IF;

    key_was_present := legacy.api_key_encrypted IS NOT NULL AND legacy.api_key_encrypted <> '';

    INSERT INTO ai_providers (name, provider_type, base_url, api_key_encrypted, enabled, is_active, connection_status,
                               last_connection_test_at, last_connection_error)
    VALUES ('Gemini (migrated)', 'GEMINI', NULL, legacy.api_key_encrypted, COALESCE(legacy.enabled, FALSE), COALESCE(legacy.enabled, FALSE),
            CASE WHEN legacy.last_test_success = TRUE THEN 'CONNECTED' ELSE 'NOT_TESTED' END,
            legacy.last_tested_at, legacy.last_test_message)
    RETURNING id INTO new_provider_id;

    INSERT INTO ai_models (ai_provider_id, model_id, display_name, default_model, default_vision_model, enabled, source)
    VALUES (new_provider_id, COALESCE(NULLIF(legacy.text_model, ''), 'gemini-1.5-flash'),
            COALESCE(NULLIF(legacy.text_model, ''), 'gemini-1.5-flash'), TRUE,
            (COALESCE(legacy.text_model, '') = COALESCE(legacy.vision_model, '')), TRUE, 'MANUAL');

    IF legacy.vision_model IS NOT NULL AND legacy.vision_model <> '' AND legacy.vision_model <> legacy.text_model THEN
        INSERT INTO ai_models (ai_provider_id, model_id, display_name, default_model, default_vision_model, enabled, source)
        VALUES (new_provider_id, legacy.vision_model, legacy.vision_model, FALSE, TRUE, TRUE, 'MANUAL');
    END IF;

    UPDATE ai_settings SET active_provider_id = new_provider_id WHERE id = legacy.id;

    INSERT INTO ai_migration_logs (source_provider, action, details, status)
    VALUES ('GEMINI', 'MIGRATE_LEGACY_AI_SETTINGS',
            json_build_object('legacyProvider', legacy.provider, 'keyMigrated', key_was_present,
                               'targetProviderId', new_provider_id)::text,
            'SUCCESS');
END $$;

ALTER TABLE ai_settings
    DROP COLUMN IF EXISTS enabled,
    DROP COLUMN IF EXISTS provider,
    DROP COLUMN IF EXISTS api_key_encrypted,
    DROP COLUMN IF EXISTS text_model,
    DROP COLUMN IF EXISTS vision_model,
    DROP COLUMN IF EXISTS timeout_seconds,
    DROP COLUMN IF EXISTS max_tokens,
    DROP COLUMN IF EXISTS last_tested_at,
    DROP COLUMN IF EXISTS last_test_success,
    DROP COLUMN IF EXISTS last_test_message,
    DROP COLUMN IF EXISTS last_test_error_code;

ALTER TABLE ai_audit_logs RENAME TO ai_audit_logs_legacy;
