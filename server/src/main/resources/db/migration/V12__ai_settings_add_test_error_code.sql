-- V12 — Add last_test_error_code column to ai_settings
-- Safe: ADD COLUMN IF NOT EXISTS — no data loss, idempotent.
ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS last_test_error_code VARCHAR(50);
