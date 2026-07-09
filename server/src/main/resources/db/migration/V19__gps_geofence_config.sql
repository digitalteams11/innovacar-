-- Add geofence & alert configuration to gps_settings
ALTER TABLE gps_settings
    ADD COLUMN IF NOT EXISTS city_lat               DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS city_lng               DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS radius_km              DOUBLE PRECISION DEFAULT 50.0,
    ADD COLUMN IF NOT EXISTS movement_threshold_m   INTEGER          DEFAULT 50,
    ADD COLUMN IF NOT EXISTS inactivity_timeout_min INTEGER          DEFAULT 30,
    ADD COLUMN IF NOT EXISTS notify_movement        BOOLEAN          DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS notify_geofence        BOOLEAN          DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS notify_offline         BOOLEAN          DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS polling_interval_sec   INTEGER          DEFAULT 30;

-- Track whether a vehicle is currently outside the allowed city zone
ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS out_of_zone BOOLEAN DEFAULT FALSE;
