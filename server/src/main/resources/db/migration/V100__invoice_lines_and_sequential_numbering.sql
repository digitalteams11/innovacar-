-- Billing redesign, Phase 1: invoice line items + safe sequential invoice
-- numbering + anti-duplication guard.
--
-- invoice_lines: an invoice's amount is no longer a single opaque number —
-- each contributing charge (rental, extension, extra km, fuel, late return,
-- damage, delivery, cleaning, discount, other) is now its own row, so the
-- invoice can show exactly what it bills instead of one lump sum. Invoice.amount
-- is kept (never renamed) as the persisted total = SUM(lines.total); nothing
-- that already reads Invoice.amount breaks.
--
-- invoice_number_sequences: per-tenant, per-year counter for readable
-- sequential invoice numbers (FAC-2026-000001), replacing the previous
-- random-UUID-suffix scheme. Incremented under a row lock (SELECT ... FOR
-- UPDATE in NumberGeneratorService) so concurrent requests can never collide —
-- count()+1 without a lock, used elsewhere in this codebase, is exactly the
-- race this table avoids for invoices.
--
-- source_type: distinguishes a CONTRACT_LINKED invoice (amount/lines derived
-- from a Contract, kept in sync by InvoiceService#syncInvoiceForContract) from
-- a MANUAL one (freely edited, never silently touched by contract-side
-- recalculation) — see Invoice entity javadoc.
--
-- uq_invoice_tenant_contract_active: at most one *active* (not CANCELLED/
-- REFUNDED) invoice per contract, enforced at the database level so a double
-- click or a race between two requests can never create two live invoices for
-- the same contract — the application-level findFirst() in
-- syncInvoiceForContract was necessary but insufficient on its own.

CREATE TABLE IF NOT EXISTS invoice_lines (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    description VARCHAR(255),
    quantity    NUMERIC(10,2) NOT NULL DEFAULT 1,
    unit_price  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total       NUMERIC(12,2) NOT NULL DEFAULT 0,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_invoice_line_invoice ON invoice_lines (invoice_id);

CREATE TABLE IF NOT EXISTS invoice_number_sequences (
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    year        INTEGER NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS notes TEXT;

UPDATE invoices SET source_type = 'CONTRACT_LINKED' WHERE contract_id IS NOT NULL;
UPDATE invoices SET subtotal_amount = amount WHERE subtotal_amount IS NULL;
UPDATE invoices SET discount_amount = 0 WHERE discount_amount IS NULL;
UPDATE invoices SET tax_amount = 0 WHERE tax_amount IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoice_tenant_contract_active
    ON invoices (tenant_id, contract_id)
    WHERE contract_id IS NOT NULL AND status NOT IN ('CANCELLED', 'REFUNDED');
