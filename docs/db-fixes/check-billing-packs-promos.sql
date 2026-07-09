-- ============================================================
-- Billing / Packs / Promo Codes — consistency check queries
-- Replace :tenantId with the numeric tenant/agency id.
-- ============================================================

-- 1. All subscription plans
SELECT id, code, name, monthly_price, yearly_price, currency,
       max_vehicles, max_employees, max_gps_devices, max_reservations,
       client_limit, contract_limit,
       whop_product_id, whop_plan_id, whop_price_id,
       is_active, display_order
FROM subscription_plans
ORDER BY display_order, id;

-- 2. Agency subscription state (tenant table)
SELECT id, name, plan_name, status,
       subscription_active, subscription_end_date,
       trial_start_date, trial_end_date,
       max_vehicles, max_employees, max_gps_devices, max_reservations
FROM tenants
ORDER BY id DESC
LIMIT 20;

-- 3. All subscription invoices
SELECT si.id, si.tenant_id, t.name AS agency_name,
       sp.name AS plan_name, si.billing_cycle,
       si.subtotal, si.discount, si.total, si.currency,
       si.status, si.coupon_code, si.issued_at, si.paid_at
FROM subscription_invoices si
LEFT JOIN tenants t  ON t.id = si.tenant_id
LEFT JOIN subscription_plans sp ON sp.id = si.plan_id
ORDER BY si.id DESC;

-- 4. All promo codes
SELECT id, code, promotion_name, promotion_type,
       discount_type, discount_value,
       free_months, applicable_plans,
       max_uses, max_uses_per_agency, used_count,
       valid_from, valid_to, is_active
FROM promo_codes
ORDER BY id DESC;

-- 5. Promo code redemptions
SELECT pcr.id, pcr.promo_code_id, pc.code AS promo_code,
       pcr.tenant_id, t.name AS agency_name,
       pcr.discount_applied, pcr.redeemed_at
FROM promo_code_redemptions pcr
LEFT JOIN promo_codes pc ON pc.id = pcr.promo_code_id
LEFT JOIN tenants t ON t.id = pcr.tenant_id
ORDER BY pcr.redeemed_at DESC
LIMIT 50;

-- 6. Usage counts for a specific tenant
SELECT
  (SELECT COUNT(*) FROM vehicles
   WHERE tenant_id = :tenantId AND COALESCE(deleted, false) = false) AS active_vehicles,

  (SELECT COUNT(*) FROM employees
   WHERE tenant_id = :tenantId AND COALESCE(deleted, false) = false) AS active_employees,

  (SELECT COUNT(*) FROM reservations
   WHERE tenant_id = :tenantId) AS total_reservations,

  (SELECT COUNT(*) FROM contracts
   WHERE tenant_id = :tenantId AND COALESCE(deleted, false) = false) AS active_contracts,

  (SELECT COUNT(*) FROM clients
   WHERE tenant_id = :tenantId AND COALESCE(deleted, false) = false) AS active_clients;

-- 7. Plan feature flags
SELECT pf.id, pf.plan_id, sp.name AS plan_name,
       pf.feature_code, pf.enabled
FROM plan_features pf
LEFT JOIN subscription_plans sp ON sp.id = pf.plan_id
ORDER BY pf.plan_id, pf.feature_code;

-- 8. Tenant feature overrides (promo/manual grants)
SELECT tfo.id, tfo.tenant_id, t.name AS agency_name,
       tfo.feature_code, tfo.enabled,
       tfo.source, tfo.starts_at, tfo.expires_at
FROM tenant_feature_overrides tfo
LEFT JOIN tenants t ON t.id = tfo.tenant_id
ORDER BY tfo.expires_at DESC NULLS LAST;
