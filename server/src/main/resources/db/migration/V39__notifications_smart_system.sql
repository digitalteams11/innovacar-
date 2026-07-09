-- ============================================================
-- V37 — Smart Notification System
-- Adds severity, module, entityType, entityId, actionUrl,
-- userId, and deduplication support to the notifications table.
-- All ALTER ... ADD COLUMN IF NOT EXISTS are idempotent.
-- ============================================================

-- New columns for rich notification data
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS severity        VARCHAR(20)  DEFAULT 'INFO';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS module          VARCHAR(30)  DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS entity_type     VARCHAR(50)  DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS entity_id       BIGINT       DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_url      VARCHAR(255) DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS user_id         BIGINT       DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at         TIMESTAMP    DEFAULT NULL;

-- Performance indexes for the new columns
CREATE INDEX IF NOT EXISTS idx_notification_tenant_type    ON notifications (tenant_id, type);
CREATE INDEX IF NOT EXISTS idx_notification_entity        ON notifications (tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_notification_severity      ON notifications (tenant_id, severity, read);
CREATE INDEX IF NOT EXISTS idx_notification_created_at    ON notifications (tenant_id, created_at DESC);
