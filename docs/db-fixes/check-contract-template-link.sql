-- Diagnostic: Contract ↔ Template linkage health check
-- Run these queries against the production/dev database to confirm
-- that template selection is persisting and resolving correctly.

-- 1. Contracts that still have no selected_template_id after backfill
SELECT c.id, c.contract_number, c.tenant_id, c.status, c.created_at
FROM   contracts c
WHERE  c.selected_template_id IS NULL
  AND  c.deleted = FALSE
ORDER  BY c.created_at DESC
LIMIT  50;

-- 2. Which template each contract resolved to
SELECT c.id            AS contract_id,
       c.contract_number,
       ct.id           AS template_id,
       ct.name         AS template_name,
       ct.template_code,
       ct.template_type,
       ct.is_default,
       ct.access_plan,
       ct.front_file_path IS NOT NULL AS has_uploaded_file
FROM   contracts c
JOIN   contract_templates ct ON ct.id = c.selected_template_id
WHERE  c.deleted = FALSE
ORDER  BY c.created_at DESC
LIMIT  50;

-- 3. Default templates per tenant
SELECT tenant_id, id, name, template_code, template_type, is_active, updated_at
FROM   contract_templates
WHERE  is_default = TRUE
ORDER  BY tenant_id, updated_at DESC;

-- 4. System templates (no uploaded file — use programmatic layout routing)
SELECT id, tenant_id, name, template_code, template_type, access_plan,
       front_file_path IS NOT NULL AS has_file
FROM   contract_templates
WHERE  template_type = 'SYSTEM_DEFAULT'
ORDER  BY tenant_id, id;

-- 5. Most recently updated template per tenant (useful to spot stale defaults)
SELECT DISTINCT ON (tenant_id)
       tenant_id, id, name, template_code, is_default, is_active, updated_at
FROM   contract_templates
ORDER  BY tenant_id, updated_at DESC;
