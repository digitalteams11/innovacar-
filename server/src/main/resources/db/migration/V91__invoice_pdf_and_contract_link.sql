ALTER TABLE invoices
    ADD COLUMN contract_id BIGINT REFERENCES contracts(id),
    ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT 'MAD',
    ADD COLUMN pdf_generated_at TIMESTAMP,
    ADD COLUMN pdf_language VARCHAR(5),
    ADD COLUMN pdf_outdated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN emailed_at TIMESTAMP,
    ADD COLUMN emailed_to VARCHAR(150);

CREATE INDEX idx_invoice_contract ON invoices(contract_id);
