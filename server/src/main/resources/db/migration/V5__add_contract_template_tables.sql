-- Create contract_templates table (Hibernate may have already created it; IF NOT EXISTS is safe).
CREATE TABLE IF NOT EXISTS contract_templates (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT,
    name                  VARCHAR(160)  NOT NULL,
    description           VARCHAR(1000),
    template_type         VARCHAR(50)   NOT NULL DEFAULT 'AGENCY_SCAN_TEMPLATE',
    template_code         VARCHAR(80),
    language              VARCHAR(10)            DEFAULT 'FR',
    pages_count           INTEGER                DEFAULT 1,
    has_conditions        BOOLEAN                DEFAULT FALSE,
    access_plan           VARCHAR(40)            DEFAULT 'STARTER',
    front_file_path       VARCHAR(1000),
    front_file_url        VARCHAR(1000),
    back_file_path        VARCHAR(1000),
    back_file_url         VARCHAR(1000),
    preview_image_url     VARCHAR(1000),
    conditions_image_url  VARCHAR(1000),
    mapping_json          TEXT,
    page_size             VARCHAR(20)            DEFAULT 'A4',
    is_default            BOOLEAN                DEFAULT FALSE,
    is_active             BOOLEAN                DEFAULT TRUE,
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP
);

-- Ensure required indexes exist for tenant-scoped lookups.
CREATE INDEX IF NOT EXISTS idx_contract_templates_tenant_id
    ON contract_templates (tenant_id);

CREATE INDEX IF NOT EXISTS idx_contract_templates_tenant_default
    ON contract_templates (tenant_id, is_default);

-- Create contract_template_fields table.
CREATE TABLE IF NOT EXISTS contract_template_fields (
    id             BIGSERIAL PRIMARY KEY,
    template_id    BIGINT         NOT NULL REFERENCES contract_templates(id) ON DELETE CASCADE,
    field_key      VARCHAR(120)   NOT NULL,
    label          VARCHAR(160),
    page_number    INTEGER        NOT NULL DEFAULT 1,
    x_percent      NUMERIC(8,4)   NOT NULL DEFAULT 0,
    y_percent      NUMERIC(8,4)   NOT NULL DEFAULT 0,
    width_percent  NUMERIC(8,4)   NOT NULL DEFAULT 30,
    height_percent NUMERIC(8,4)   NOT NULL DEFAULT 4,
    font_size      INTEGER        NOT NULL DEFAULT 10,
    font_family    VARCHAR(80)             DEFAULT 'Helvetica',
    font_weight    VARCHAR(30)             DEFAULT 'normal',
    text_align     VARCHAR(20)             DEFAULT 'left',
    color          VARCHAR(20)             DEFAULT '#000000',
    multiline      BOOLEAN                 DEFAULT FALSE,
    date_format    VARCHAR(40),
    enabled        BOOLEAN                 DEFAULT TRUE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_contract_template_fields_template_id
    ON contract_template_fields (template_id);

-- Create contract_terms table.
CREATE TABLE IF NOT EXISTS contract_terms (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT,
    title          VARCHAR(180),
    content        TEXT,
    language       VARCHAR(10)   DEFAULT 'FR',
    version        VARCHAR(40),
    default_terms  BOOLEAN       DEFAULT FALSE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_contract_terms_tenant_id
    ON contract_terms (tenant_id);

-- Add selected_template_id FK column to contracts table if it does not exist.
ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS selected_template_id BIGINT
        REFERENCES contract_templates(id) ON DELETE SET NULL;
