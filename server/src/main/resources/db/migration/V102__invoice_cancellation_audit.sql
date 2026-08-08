-- Billing fixes, Phase 2: invoice cancellation must be auditable (who/when/why),
-- never a silent status flip — see InvoiceService#cancelInvoice. Mirrors the
-- same columns already added to contracts in V101.

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cancelled_by_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
