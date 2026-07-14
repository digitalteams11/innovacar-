-- Legal / privacy / cookie-consent / user-rules / security compliance module.
-- Documents are platform-wide (published once by Super Admin), not per-tenant;
-- acceptance, cookie-consent and privacy-request records are per-user/tenant
-- for audit and reporting purposes.

CREATE TABLE IF NOT EXISTS legal_document_versions (
    id                  BIGSERIAL PRIMARY KEY,
    document_type       VARCHAR(60)  NOT NULL,
    locale              VARCHAR(5)   NOT NULL,
    version_number      INTEGER      NOT NULL,
    title               VARCHAR(300) NOT NULL,
    content_html        TEXT         NOT NULL,
    summary_of_changes  TEXT,
    material            BOOLEAN      NOT NULL DEFAULT FALSE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    effective_date      DATE,
    published_at        TIMESTAMP,
    created_by_user_id  BIGINT,
    created_by_email    VARCHAR(200),
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    CONSTRAINT uq_legal_doc_type_locale_version UNIQUE (document_type, locale, version_number)
);

CREATE INDEX IF NOT EXISTS idx_legal_doc_type_locale_status
    ON legal_document_versions (document_type, locale, status);

CREATE TABLE IF NOT EXISTS legal_acceptances (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    tenant_id           BIGINT,
    document_type       VARCHAR(60)  NOT NULL,
    locale              VARCHAR(5)   NOT NULL,
    version_number      INTEGER      NOT NULL,
    document_version_id BIGINT       NOT NULL REFERENCES legal_document_versions(id),
    accepted_at         TIMESTAMP    NOT NULL,
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    capture_context     VARCHAR(40)
);

CREATE INDEX IF NOT EXISTS idx_legal_acceptance_user_type ON legal_acceptances (user_id, document_type);
CREATE INDEX IF NOT EXISTS idx_legal_acceptance_tenant ON legal_acceptances (tenant_id);

CREATE TABLE IF NOT EXISTS cookie_consents (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT,
    tenant_id             BIGINT,
    anonymous_id          VARCHAR(64),
    functional            BOOLEAN NOT NULL DEFAULT FALSE,
    analytics             BOOLEAN NOT NULL DEFAULT FALSE,
    marketing             BOOLEAN NOT NULL DEFAULT FALSE,
    policy_version_number INTEGER,
    ip_address            VARCHAR(64),
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cookie_consent_anonymous ON cookie_consents (anonymous_id) WHERE anonymous_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_cookie_consent_user ON cookie_consents (user_id) WHERE user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS privacy_requests (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    tenant_id             BIGINT,
    request_type          VARCHAR(30)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    details               TEXT,
    requested_at          TIMESTAMP    NOT NULL,
    resolved_at           TIMESTAMP,
    resolved_by_user_id   BIGINT,
    resolution_notes      TEXT
);

CREATE INDEX IF NOT EXISTS idx_privacy_request_user ON privacy_requests (user_id);
CREATE INDEX IF NOT EXISTS idx_privacy_request_status ON privacy_requests (status);

CREATE TABLE IF NOT EXISTS data_retention_policy_entries (
    id              BIGSERIAL PRIMARY KEY,
    data_category   VARCHAR(150) NOT NULL,
    retention_period VARCHAR(150) NOT NULL,
    legal_basis     TEXT,
    display_order   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

-- Starter retention schedule — placeholders to be reviewed/adjusted by legal counsel.
INSERT INTO data_retention_policy_entries (data_category, retention_period, legal_basis, display_order, created_at, updated_at)
SELECT * FROM (VALUES
    ('Account and identity data', 'Duration of the account, plus 5 years after closure', 'Contractual necessity and Moroccan commercial record-keeping obligations', 1, now(), now()),
    ('Rental contracts and signatures', '10 years after contract end', 'Moroccan commercial/tax record-keeping obligations', 2, now(), now()),
    ('Payment and invoicing records', '10 years after transaction', 'Moroccan tax and accounting obligations', 3, now(), now()),
    ('GPS/geolocation history', '12 months, unless retained longer for an active dispute or investigation', 'Legitimate interest in fleet security, limited by proportionality', 4, now(), now()),
    ('Support tickets and communications', '3 years after closure', 'Legitimate interest in service quality and dispute resolution', 5, now(), now()),
    ('Cookie and consent records', '13 months from consent, renewed on next visit', 'CNDP cookie-compliance guidance', 6, now(), now())
) AS seed(data_category, retention_period, legal_basis, display_order, created_at, updated_at)
WHERE NOT EXISTS (SELECT 1 FROM data_retention_policy_entries);
