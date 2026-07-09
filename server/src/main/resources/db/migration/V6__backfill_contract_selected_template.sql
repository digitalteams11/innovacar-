-- Backfill selected_template_id on existing contracts that have none,
-- using the tenant's current default active template (if one exists).
-- Safe to run multiple times: WHERE clause limits to unset rows.
UPDATE contracts c
SET    selected_template_id = (
           SELECT ct.id
           FROM   contract_templates ct
           WHERE  ct.tenant_id       = c.tenant_id
             AND  ct.is_default      = TRUE
             AND  ct.is_active       = TRUE
           ORDER  BY ct.updated_at DESC
           LIMIT  1
       )
WHERE  c.selected_template_id IS NULL
  AND  c.deleted = FALSE;
