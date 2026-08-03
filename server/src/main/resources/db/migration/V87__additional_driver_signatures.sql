-- ============================================================
-- V87 — Independent digital-signing workflow for additional drivers.
-- Adds signature/token/audit-timestamp columns directly onto
-- contract_additional_drivers (no new generic signature table — mirrors
-- the existing flat-column convention used by Contract's own client/agency
-- signature fields). See AdditionalDriverSigningService for the workflow.
-- ============================================================

ALTER TABLE contract_additional_drivers
    ADD COLUMN email VARCHAR(150),
    ADD COLUMN signature_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN signature_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN signature_data TEXT,
    ADD COLUMN signed_at TIMESTAMP,
    ADD COLUMN signed_ip VARCHAR(50),
    ADD COLUMN signed_user_agent VARCHAR(255),
    ADD COLUMN declaration_version VARCHAR(20),
    ADD COLUMN declarations_accepted TEXT,
    ADD COLUMN token_hash VARCHAR(128),
    ADD COLUMN token_expires_at TIMESTAMP,
    ADD COLUMN token_revoked_at TIMESTAMP,
    ADD COLUMN link_sent_at TIMESTAMP,
    ADD COLUMN opened_at TIMESTAMP,
    ADD COLUMN declined_at TIMESTAMP,
    ADD COLUMN decline_reason VARCHAR(255),
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP;

CREATE UNIQUE INDEX idx_additional_driver_token_hash ON contract_additional_drivers(token_hash);
CREATE INDEX idx_additional_driver_contract ON contract_additional_drivers(contract_id);
