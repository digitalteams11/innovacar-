-- Removes the "Contract Templates" feature: agencies now always use the
-- single canonical built-in contract PDF layout, so the agency-uploaded
-- template, field-mapping, and terms-editor tables are no longer needed.

ALTER TABLE contracts DROP COLUMN IF EXISTS selected_template_id;

DROP TABLE IF EXISTS contract_template_fields CASCADE;
DROP TABLE IF EXISTS contract_templates CASCADE;
DROP TABLE IF EXISTS contract_terms CASCADE;
