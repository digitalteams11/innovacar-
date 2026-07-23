-- Adds automated email/WhatsApp delivery tracking to the "client self-fill
-- information" workflow (see ClientInformationRequestService), plus the
-- OPENED/REJECTED lifecycle states and an optional link to a pre-existing
-- Client so the admin can prefill the request from a known client.
ALTER TABLE client_information_requests
    ADD COLUMN client_id                    BIGINT REFERENCES clients(id),
    ADD COLUMN delivery_channels            VARCHAR(50),
    ADD COLUMN email_delivery_status        VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN whatsapp_delivery_status     VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN email_last_error             VARCHAR(255),
    ADD COLUMN whatsapp_last_error          VARCHAR(255),
    ADD COLUMN email_sent_at                TIMESTAMP,
    ADD COLUMN whatsapp_sent_at             TIMESTAMP,
    ADD COLUMN email_last_attempt_at        TIMESTAMP,
    ADD COLUMN whatsapp_last_attempt_at     TIMESTAMP,
    ADD COLUMN opened_at                    TIMESTAMP,
    ADD COLUMN rejected_at                  TIMESTAMP,
    ADD COLUMN reminder_not_opened_sent_at  TIMESTAMP,
    ADD COLUMN reminder_expiry_sent_at      TIMESTAMP;

CREATE INDEX idx_client_info_requests_client ON client_information_requests(client_id);
