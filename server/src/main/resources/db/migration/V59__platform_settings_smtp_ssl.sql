-- Adds explicit implicit-SSL (port 465) support alongside the existing STARTTLS (smtp_use_tls)
-- flag, so Super Admin can select SSL mode for providers/plans that require it instead of
-- STARTTLS on 587. Defaults to false (existing STARTTLS configs are unaffected).
ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS smtp_ssl_enabled BOOLEAN DEFAULT FALSE;
