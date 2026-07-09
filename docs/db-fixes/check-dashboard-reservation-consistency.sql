-- ============================================================
-- Dashboard ↔ Reservations consistency check
-- Run these queries when Dashboard reservation count differs
-- from the Reservations page row count.
-- Replace :tenantId with the numeric tenant/agency id.
-- ============================================================

-- 1. Total reservations for a tenant (matches DashboardService count)
SELECT COUNT(*) AS total_reservations
FROM reservations
WHERE tenant_id = :tenantId;

-- 2. Reservations grouped by status (matches Reservations page tabs)
SELECT status, COUNT(*) AS cnt
FROM reservations
WHERE tenant_id = :tenantId
GROUP BY status
ORDER BY cnt DESC;

-- 3. Contracts that have no linked reservation (sync candidates)
--    ReservationService.syncReservationsFromContractsForCurrentTenant() should
--    backfill these automatically on the next GET /api/reservations call.
SELECT c.id          AS contract_id,
       c.status      AS contract_status,
       c.start_date,
       c.end_date,
       c.vehicle_id,
       c.client_id
FROM contracts c
WHERE c.tenant_id = :tenantId
  AND c.reservation_id IS NULL
  AND c.deleted = false
ORDER BY c.id DESC;

-- 4. Vehicle counts by status (matches DashboardService vehicle metrics)
SELECT statut, COUNT(*) AS cnt
FROM vehicles
WHERE tenant_id = :tenantId
  AND (deleted IS NULL OR deleted = false)
GROUP BY statut
ORDER BY cnt DESC;

-- 5. Available vehicles (null status treated as AVAILABLE — matches countAvailableByTenantId)
SELECT COUNT(*) AS available_vehicles
FROM vehicles
WHERE tenant_id = :tenantId
  AND (deleted IS NULL OR deleted = false)
  AND (statut = 'AVAILABLE' OR statut IS NULL);

-- 6. Dashboard KPI snapshot
SELECT
  (SELECT COUNT(*) FROM vehicles   WHERE tenant_id = :tenantId AND (deleted IS NULL OR deleted = false))  AS fleet,
  (SELECT COUNT(*) FROM vehicles   WHERE tenant_id = :tenantId AND (deleted IS NULL OR deleted = false) AND (statut = 'AVAILABLE' OR statut IS NULL)) AS available,
  (SELECT COUNT(*) FROM vehicles   WHERE tenant_id = :tenantId AND (deleted IS NULL OR deleted = false) AND statut = 'RENTED')    AS rented,
  (SELECT COUNT(*) FROM vehicles   WHERE tenant_id = :tenantId AND (deleted IS NULL OR deleted = false) AND statut = 'RESERVED')  AS reserved,
  (SELECT COUNT(*) FROM reservations WHERE tenant_id = :tenantId) AS total_reservations,
  (SELECT COUNT(*) FROM clients    WHERE tenant_id = :tenantId AND (deleted IS NULL OR deleted = false))  AS total_clients;
