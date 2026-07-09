# API Stabilization Report

Scope: core platform stabilization only. No AI/GPS/design expansion.

## Audited Endpoints

| Endpoint | Frontend caller | Backend controller | Security | Current finding | Fix applied |
| --- | --- | --- | --- | --- | --- |
| GET /api/reservations | frontend-web/src/pages/Reservations.tsx | ReservationController#getReservations | Authenticated agency user | Controller exists. Returns tenant-scoped DTO list. A visible 404 is likely stale frontend/backend build or wrong server instance, not missing source route. | Kept route stable; response already uses ApiResponse and empty list is 200. Documented as canonical endpoint. |
| GET /api/contracts | frontend-web/src/pages/Contracts.tsx | ContractController#listContracts | Authenticated agency user | Returned bare array while platform uses standard envelopes; frontend expected only bare array. | Now returns ApiResponse<List<ContractResponse>>. Frontend unwraps both envelope and bare array. |
| GET /api/contracts/{id} | frontend-web/src/pages/ContractDetails.tsx | ContractController#getContract | Authenticated agency user | Returned bare DTO while list/detail response shapes diverged. Some legacy partial rows could fail DTO mapping on null tenant. | Now returns ApiResponse<ContractResponse>. Frontend unwraps envelope and shows clean 404 message. ContractResponse tenantId mapping is null-safe. |
| GET /api/permissions/matrix | frontend-web/src/pages/RolePermissions.tsx | RolePermissionController#matrix | MANAGE_EMPLOYEES | Default permission repair can fail and bubble into a 500. | Matrix now logs default-repair failure and still returns a stable empty/partial matrix payload. |
| GET /api/dashboard | frontend-web/src/pages/Dashboard.tsx | DashboardController#getDashboardMetrics | Authenticated agency user | One failing repository query could crash the whole dashboard. Some expected alias keys were missing. | DashboardService now isolates metric failures with safe defaults while keeping real DB counts. DashboardController adds fleet/reservations/depositsHeld/deductions aliases. |
| GET /api/super-admin/data-reset/status | frontend-web/src/pages/superadmin/SuperAdminDataReset.tsx | SuperAdminDataResetController#status | SUPER_ADMIN | Existing route present. | Verified existing secure status endpoint. |
| POST /api/super-admin/data-reset/preview | frontend-web/src/pages/superadmin/SuperAdminDataReset.tsx | SuperAdminDataResetController#preview | SUPER_ADMIN + Super Owner in service | Existing route present. | Verified preview counts and confirmation code behavior. |
| POST /api/super-admin/data-reset/execute | frontend-web/src/pages/superadmin/SuperAdminDataReset.tsx | SuperAdminDataResetController#execute | SUPER_ADMIN + Super Owner + email verified + 2FA + password + confirmation code | Existing route present with audit and production platform-reset block. | Verified enforcement remains in SuperAdminDataResetService. |
| GET /api/super-admin/data-reset/audit-logs | frontend-web/src/pages/superadmin/SuperAdminDataReset.tsx | SuperAdminDataResetController#auditLogs | SUPER_ADMIN | Existing route present. | Verified audit log endpoint. |

## Notes

- Contract list and detail now share the same response standard and tenant-scoped service path.
- Deleted contracts are already hidden by the Contract entity SQL restriction; visible contracts should now unwrap/open consistently.
- Super Admin Data Reset Center already existed. This pass did not create a duplicate reset system.
- No destructive data operation was executed.
- No backend or frontend server was started.
