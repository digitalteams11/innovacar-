-- Add Traccar / Custom-API-specific credential fields to existing gps_settings
ALTER TABLE gps_settings
    ADD COLUMN IF NOT EXISTS encrypted_password VARCHAR(500),
    ADD COLUMN IF NOT EXISTS auth_header_name   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS auth_prefix        VARCHAR(50);

-- Standalone GPS-device table (synced from provider; may or may not link to a vehicle)
CREATE TABLE IF NOT EXISTS gps_devices (
    id                 BIGSERIAL    PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    vehicle_id         BIGINT       REFERENCES vehicles(id) ON DELETE SET NULL,
    provider_device_id VARCHAR(200) NOT NULL,
    imei               VARCHAR(100),
    name               VARCHAR(200),
    plate_number       VARCHAR(50),
    status             VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    speed              DOUBLE PRECISION,
    ignition           BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at       TIMESTAMP,
    last_synced_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_gps_devices_tenant_provider
    ON gps_devices (tenant_id, provider_device_id);

CREATE INDEX IF NOT EXISTS idx_gps_devices_tenant
    ON gps_devices (tenant_id);

CREATE INDEX IF NOT EXISTS idx_gps_devices_vehicle
    ON gps_devices (vehicle_id) WHERE vehicle_id IS NOT NULL;
