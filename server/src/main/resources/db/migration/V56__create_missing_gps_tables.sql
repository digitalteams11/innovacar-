-- ============================================================
-- V56 — Create the gps_settings and gps_alerts base tables.
--
-- Root cause: V19/V33/V34 only ever ran ALTER TABLE / CREATE INDEX
-- against gps_settings and gps_alerts, assuming the tables already
-- existed. They were never created by a migration — only by
-- Hibernate's ddl-auto=update on environments that happened to run
-- with that setting. On any database where Flyway is the sole
-- source of schema (ddl-auto=validate, or a fresh database), these
-- tables never exist, so every GPS settings/stats query — including
-- the scheduler's every-60s "WHERE gps_settings.enabled" poll —
-- fails with "relation does not exist", surfacing as repeated 500s.
--
-- Safe: CREATE TABLE IF NOT EXISTS — a no-op wherever the tables
-- already exist, so it does not touch or delete any existing data.
-- ============================================================

CREATE TABLE IF NOT EXISTS gps_settings (
    id                       BIGSERIAL      PRIMARY KEY,
    tenant_id                BIGINT         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider                 VARCHAR(30)    NOT NULL,
    app_id                   VARCHAR(200),
    api_key_encrypted        VARCHAR(500),
    base_url                 VARCHAR(300),
    device_group_id          VARCHAR(100),
    webhook_url              VARCHAR(300),
    connection_status        VARCHAR(20)    DEFAULT 'DISCONNECTED',
    last_sync_at             TIMESTAMP,
    last_tested_at           TIMESTAMP,
    active_devices           INTEGER        DEFAULT 0,
    last_error               VARCHAR(500),
    encrypted_password       VARCHAR(500),
    auth_header_name         VARCHAR(100),
    auth_prefix              VARCHAR(50),
    -- Defaults to FALSE, matching the entity default and the "connect
    -- then explicitly enable" flow in GpsSettingsService — a tenant
    -- must never end up GPS-enabled without configuring it themselves.
    enabled                  BOOLEAN        NOT NULL DEFAULT FALSE,
    city_lat                 DOUBLE PRECISION,
    city_lng                 DOUBLE PRECISION,
    radius_km                DOUBLE PRECISION DEFAULT 50.0,
    movement_threshold_m     INTEGER        DEFAULT 50,
    inactivity_timeout_min   INTEGER        DEFAULT 30,
    notify_movement          BOOLEAN        DEFAULT FALSE,
    notify_geofence          BOOLEAN        DEFAULT FALSE,
    notify_offline           BOOLEAN        DEFAULT FALSE,
    polling_interval_sec     INTEGER        DEFAULT 30,
    created_at               TIMESTAMP,
    updated_at               TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_gps_settings_tenant ON gps_settings (tenant_id);

CREATE TABLE IF NOT EXISTS gps_alerts (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    alert_type    VARCHAR(30)  NOT NULL,
    message       VARCHAR(255) NOT NULL,
    severity      VARCHAR(20),
    read          BOOLEAN      NOT NULL DEFAULT FALSE,
    vehicle_id    BIGINT,
    vehicle_name  VARCHAR(150),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    speed         DOUBLE PRECISION,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gps_alert_tenant  ON gps_alerts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_gps_alert_vehicle ON gps_alerts (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_gps_alert_type    ON gps_alerts (alert_type);
CREATE INDEX IF NOT EXISTS idx_gps_alert_read    ON gps_alerts (tenant_id, read);

-- V34 backfilled `enabled` to TRUE by mistake for any environment where
-- this ADD COLUMN actually ran (table pre-existed there via ddl-auto).
-- That silently turned GPS tracking on for tenants who never configured
-- or tested a provider. Correct it: only for rows with no stored
-- credentials at all (i.e. never actually connected), and only if the
-- column's default is still the old erroneous TRUE.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'gps_settings' AND column_name = 'enabled' AND column_default = 'true'
    ) THEN
        UPDATE gps_settings
        SET enabled = FALSE
        WHERE enabled = TRUE
          AND (api_key_encrypted IS NULL OR api_key_encrypted = '')
          AND (encrypted_password IS NULL OR encrypted_password = '');

        ALTER TABLE gps_settings ALTER COLUMN enabled SET DEFAULT FALSE;
    END IF;
END $$;
