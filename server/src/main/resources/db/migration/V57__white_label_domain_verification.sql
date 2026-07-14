ALTER TABLE white_label_settings
    ADD COLUMN IF NOT EXISTS subdomain VARCHAR(63) UNIQUE,
    ADD COLUMN IF NOT EXISTS verification_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS dns_verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_checked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_check_error TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_white_label_subdomain ON white_label_settings (subdomain);
