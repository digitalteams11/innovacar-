-- Normalizes client identity documents: replaces the old "CIN or passport"
-- OR-rule (separate clients.cin / clients.passport_number columns) with a
-- single document_type + document_number model, and extends driver-license
-- data with category/country. Existing cin/passport_number/driving_license*
-- columns are kept in place (not dropped) so the legacy desktop app keeps
-- working unchanged during the transition.

CREATE TABLE IF NOT EXISTS client_identity_documents (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    client_id BIGINT NOT NULL REFERENCES clients(id),
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('CIN','PASSPORT','RESIDENCE_PERMIT','OTHER')),
    document_number VARCHAR(64) NOT NULL,
    issuing_country VARCHAR(100),
    issuing_authority VARCHAR(150),
    issue_date DATE,
    expiry_date DATE,
    front_file_id VARCHAR(255),
    back_file_id VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_client_identity_documents_tenant ON client_identity_documents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_client_identity_documents_client ON client_identity_documents(client_id);

-- Only the primary document per client needs to be unique within a tenant;
-- historical/non-primary documents (e.g. an expired passport kept on file)
-- must not collide with that uniqueness rule.
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_identity_documents_tenant_number
    ON client_identity_documents (tenant_id, lower(document_number))
    WHERE is_primary = TRUE;

-- Records clients whose legacy cin AND passport_number were both populated,
-- so the non-primary one backfilled below can be reviewed manually instead
-- of being silently dropped.
CREATE TABLE IF NOT EXISTS client_identity_backfill_report (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients(id),
    note VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Backfill: CIN becomes the primary document when present.
INSERT INTO client_identity_documents (tenant_id, client_id, document_type, document_number, is_primary)
SELECT tenant_id, id, 'CIN', cin, TRUE
FROM clients
WHERE cin IS NOT NULL AND btrim(cin) <> '';

-- Passport becomes primary only when no CIN was present; otherwise it is
-- backfilled as a non-primary historical record and flagged for review.
INSERT INTO client_identity_documents (tenant_id, client_id, document_type, document_number, is_primary)
SELECT c.tenant_id, c.id, 'PASSPORT', c.passport_number, (c.cin IS NULL OR btrim(c.cin) = '')
FROM clients c
WHERE c.passport_number IS NOT NULL AND btrim(c.passport_number) <> '';

INSERT INTO client_identity_backfill_report (client_id, note)
SELECT id, 'Both CIN and passport were populated pre-migration; CIN kept as primary document, passport backfilled as non-primary for manual review.'
FROM clients
WHERE cin IS NOT NULL AND btrim(cin) <> ''
  AND passport_number IS NOT NULL AND btrim(passport_number) <> '';

-- Driver license: extend with category/issuing country (kept flat on
-- clients, not split into a separate table — one client has one active
-- license at a time, no history requirement was raised for this pass).
ALTER TABLE clients ADD COLUMN IF NOT EXISTS driving_license_category VARCHAR(20);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS driving_license_country VARCHAR(100);
