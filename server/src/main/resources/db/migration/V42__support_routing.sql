-- Help/Support/Contact Center: channel-aware ticket routing and per-channel destination emails.
-- IF NOT EXISTS: a manually-bootstrapped database (see
-- server/scripts/bootstrap/baseline-core-schema.sql) may already contain
-- these columns as part of the current entity shape, so this must be safe
-- to run against both a database that has them and one that doesn't.

ALTER TABLE support_tickets
    ADD COLUMN IF NOT EXISTS channel VARCHAR(20),
    ADD COLUMN IF NOT EXISTS destination_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS requester_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS requester_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS requester_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS related_contract_id BIGINT,
    ADD COLUMN IF NOT EXISTS related_reservation_id BIGINT,
    ADD COLUMN IF NOT EXISTS related_vehicle_id BIGINT;

ALTER TABLE platform_settings
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS technical_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS billing_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS security_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS fallback_email VARCHAR(255);

ALTER TABLE email_logs
    ADD COLUMN IF NOT EXISTS ticket_id BIGINT;
