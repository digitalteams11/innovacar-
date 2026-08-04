-- ============================================================
-- V89 — Separates email delivery status from signature status on
-- contract_additional_drivers. signature_status (PENDING/LINK_SENT/OPENED/
-- SIGNED/...) describes the signing workflow and was never proof the email
-- actually reached the driver's inbox — the "Lien envoyé" badge was set
-- purely by persisting signature_status=LINK_SENT with no real email send.
-- These new columns track the real provider-confirmed delivery outcome
-- separately. See AdditionalDriverSigningService / EmailService.
-- ============================================================

ALTER TABLE contract_additional_drivers
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SENT',
    ADD COLUMN last_delivery_channel VARCHAR(20),
    ADD COLUMN last_sent_at TIMESTAMP,
    ADD COLUMN provider_message_id VARCHAR(150),
    ADD COLUMN delivery_failure_code VARCHAR(50),
    ADD COLUMN delivery_failure_message_safe VARCHAR(255),
    ADD COLUMN last_delivery_attempt_at TIMESTAMP,
    ADD COLUMN delivery_attempt_count INTEGER NOT NULL DEFAULT 0;
