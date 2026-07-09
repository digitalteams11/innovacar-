-- Branches: add isDefault flag and updatedAt timestamp
ALTER TABLE branches
    ADD COLUMN IF NOT EXISTS is_default  BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP;

-- Subscription plans: add per-plan branch cap
ALTER TABLE subscription_plans
    ADD COLUMN IF NOT EXISTS max_branches INT;

-- Tenants: add admin-override branch cap
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS max_branches INT;

-- Seed plan limits: Trial=1, Basic=1, Standard=2, Premium=NULL (unlimited)
UPDATE subscription_plans SET max_branches = 1 WHERE LOWER(name) IN ('trial', 'basic');
UPDATE subscription_plans SET max_branches = 2 WHERE LOWER(name) = 'standard';
-- Premium intentionally left NULL = unlimited

-- Mark the first (oldest) branch per tenant as default for existing data
UPDATE branches b
SET is_default = TRUE
WHERE b.id IN (
    SELECT DISTINCT ON (tenant_id) id
    FROM branches
    ORDER BY tenant_id, id ASC
);
