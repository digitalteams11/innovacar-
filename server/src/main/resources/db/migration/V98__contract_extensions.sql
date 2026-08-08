-- Financial Control System, Phase 2: rental extensions ("Prolonger la
-- location") as a first-class, auditable financial line item.
--
-- A contract extension must never overwrite the original rental history —
-- Contract.total_price/rental_days/end_date are updated in place to the new
-- current total (exactly as every other part of the system already treats
-- Contract as the live financial state), but this table preserves the
-- original breakdown: what was added, when, by whom, and why, independent
-- of the contract's own mutable fields. Payment history is never touched
-- by an extension — only the contract's remaining balance changes, via the
-- existing PaymentService#recalculateContractFinancials.

CREATE TABLE IF NOT EXISTS contract_extensions (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT NOT NULL REFERENCES contracts(id),
    tenant_id           BIGINT NOT NULL REFERENCES tenants(id),
    additional_days     INTEGER NOT NULL,
    additional_amount   NUMERIC(12,2) NOT NULL,
    previous_end_date   DATE NOT NULL,
    new_end_date        DATE NOT NULL,
    reason              VARCHAR(500),
    performed_by        VARCHAR(255),
    performed_by_id     BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contract_extension_contract ON contract_extensions (contract_id);
CREATE INDEX IF NOT EXISTS idx_contract_extension_tenant ON contract_extensions (tenant_id);
