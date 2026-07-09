-- ============================================================
-- V25 — Email template system enhancements
-- Adds templateKey, language, systemDefault, TEXT body columns.
-- Safe: ADD COLUMN IF NOT EXISTS, ALTER TYPE only where needed.
-- ============================================================

-- 1. New columns on email_templates
ALTER TABLE email_templates
    ADD COLUMN IF NOT EXISTS template_key    VARCHAR(80),
    ADD COLUMN IF NOT EXISTS language        VARCHAR(10)  NOT NULL DEFAULT 'EN',
    ADD COLUMN IF NOT EXISTS system_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_by      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by      VARCHAR(100);

-- 2. Promote body columns from VARCHAR(5000) to TEXT for full HTML bodies.
--    PostgreSQL: cast is safe when existing data fits (it always will).
ALTER TABLE email_templates
    ALTER COLUMN body_html  TYPE TEXT,
    ALTER COLUMN body_text  TYPE TEXT;

-- 3. Widen subject column
ALTER TABLE email_templates
    ALTER COLUMN subject TYPE VARCHAR(300);

-- 4. Unique index: one system template per key+language pair.
--    Partial index excludes rows where template_key is NULL (legacy rows).
CREATE UNIQUE INDEX IF NOT EXISTS uq_email_templates_key_lang
    ON email_templates (template_key, language)
    WHERE template_key IS NOT NULL;
