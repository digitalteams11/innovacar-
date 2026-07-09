-- Help/Support/Contact Center: channel-aware ticket routing and per-channel destination emails.

ALTER TABLE support_tickets
    ADD COLUMN channel VARCHAR(20),
    ADD COLUMN destination_email VARCHAR(255),
    ADD COLUMN email_status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN requester_name VARCHAR(255),
    ADD COLUMN requester_email VARCHAR(255),
    ADD COLUMN requester_phone VARCHAR(50),
    ADD COLUMN related_contract_id BIGINT,
    ADD COLUMN related_reservation_id BIGINT,
    ADD COLUMN related_vehicle_id BIGINT;

ALTER TABLE platform_settings
    ADD COLUMN contact_email VARCHAR(255),
    ADD COLUMN technical_email VARCHAR(255),
    ADD COLUMN billing_email VARCHAR(255),
    ADD COLUMN security_email VARCHAR(255),
    ADD COLUMN fallback_email VARCHAR(255);

ALTER TABLE email_logs
    ADD COLUMN ticket_id BIGINT;
