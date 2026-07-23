-- "Client self-fill information" workflow (MVP slice): the agency can send a
-- secure link so a client fills their own contact/identity/license info
-- without an account; the agency reviews and approves before it touches a
-- real client or contract. See ClientInformationRequest.java for design notes.
CREATE TABLE client_information_requests (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenants(id),
    token_hash           VARCHAR(64) NOT NULL UNIQUE,
    temporary_name       VARCHAR(150),
    phone                VARCHAR(50),
    email                VARCHAR(150),
    preferred_language   VARCHAR(5),
    status               VARCHAR(20) NOT NULL DEFAULT 'SENT',
    expires_at           TIMESTAMP NOT NULL,
    submitted_at         TIMESTAMP,
    approved_at          TIMESTAMP,
    revoked_at           TIMESTAMP,
    contract_id          BIGINT REFERENCES contracts(id),
    approved_client_id   BIGINT REFERENCES clients(id),
    created_by_user_id   BIGINT,
    submission_payload   TEXT,
    privacy_accepted_at  TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_client_info_requests_tenant ON client_information_requests(tenant_id);
CREATE INDEX idx_client_info_requests_contract ON client_information_requests(contract_id);
CREATE INDEX idx_client_info_requests_status ON client_information_requests(status);
