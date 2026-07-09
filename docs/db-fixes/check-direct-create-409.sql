-- ============================================================
-- DIRECT-CREATE 409 DIAGNOSTIC QUERIES
-- Run these against the database to identify the source of a
-- 409 Conflict returned by POST /api/contracts/direct-create.
-- ============================================================

-- 1. CHECK EXISTING CLIENTS — find potential duplicates for the submitted newClient
--    Compare phone / CIN / passport / driver_license / email from the payload.
SELECT
    id,
    name         AS full_name,
    phone,
    cin,
    passport_number,
    driving_license  AS driver_license_number,
    email,
    COALESCE(deleted, false) AS deleted
FROM clients
WHERE COALESCE(deleted, false) = false
  AND tenant_id = :tenantId   -- replace with actual tenant id
ORDER BY id DESC;

-- Quick duplicate check for the exact payload values:
SELECT id, name, phone, cin, driving_license, email, deleted
FROM clients
WHERE COALESCE(deleted, false) = false
  AND tenant_id = :tenantId
  AND (
      LOWER(phone)           = LOWER('0658742744')
   OR LOWER(cin)             = LOWER('jy34566')
   OR LOWER(driving_license) = LOWER('546777888')
   OR LOWER(email)           = LOWER('simo1amddah@gmail.com')
  );


-- 2. CHECK VEHICLE SELECTED IN PAYLOAD (vehicleId = 10)
SELECT
    id,
    marque,
    modele,
    statut,
    prix_jour    AS daily_price,
    COALESCE(deleted, false) AS deleted
FROM vehicles
WHERE id = 10
  AND tenant_id = :tenantId;


-- 3. CHECK CONTRACTS for vehicle 10 that overlap 2026-06-27 → 2026-07-31
SELECT
    id,
    contract_number,
    status          AS contract_status,
    vehicle_id,
    start_date,
    end_date,
    COALESCE(deleted, false) AS deleted
FROM contracts
WHERE vehicle_id = 10
  AND tenant_id = :tenantId
  AND COALESCE(deleted, false) = false
  AND status NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED')
  AND start_date <= '2026-07-31'
  AND end_date   >= '2026-06-27'
ORDER BY id DESC;


-- 4. CHECK RESERVATIONS for vehicle 10 that overlap 2026-06-27 → 2026-07-31
SELECT
    id,
    status,
    vehicle_id,
    date_start   AS start_date,
    date_end     AS end_date,
    COALESCE(deleted, false) AS deleted
FROM reservations
WHERE vehicle_id = 10
  AND tenant_id = :tenantId
  AND COALESCE(deleted, false) = false
  AND status NOT IN ('CANCELLED', 'COMPLETED', 'RETURNED', 'REJECTED', 'EXPIRED')
  AND date_start <= '2026-07-31'
  AND date_end   >= '2026-06-27'
ORDER BY id DESC;


-- 5. CHECK MAINTENANCE for vehicle 10 overlapping the requested period
SELECT
    id,
    status,
    vehicle_id,
    scheduled_date  AS start_date,
    completion_date AS end_date
FROM maintenance
WHERE vehicle_id = 10
  AND tenant_id = :tenantId
  AND status IN ('SCHEDULED', 'IN_PROGRESS')
  AND scheduled_date  <= '2026-07-31'
  AND (completion_date IS NULL OR completion_date >= '2026-06-27')
ORDER BY id DESC;


-- 6. CHECK SELECTED TEMPLATE (selectedTemplateId = 9)
SELECT
    id,
    name,
    tenant_id,
    is_active,
    template_type,
    access_plan     AS required_plan,
    is_default
FROM contract_templates
WHERE id = 9;
