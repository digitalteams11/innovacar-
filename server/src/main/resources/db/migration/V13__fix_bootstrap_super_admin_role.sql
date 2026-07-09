-- ============================================================
-- V13 — Ensure bootstrap SUPER_ADMIN accounts are unrestricted
-- ============================================================
-- The isSuperOwner() check in SuperAdminDataResetService treats
-- super_admin_role_id = NULL as "legacy/unrestricted super admin"
-- (full access — backward compat for accounts created before the
-- staff-role system). If a bootstrap account was inadvertently
-- assigned a non-SUPER_OWNER sub-role (e.g. the "SUPER_ADMIN"
-- staff sub-role whose code != "SUPER_OWNER") it silently loses
-- access to the Data Reset Center and other owner-gated features.
-- This migration repairs that by clearing the sub-role assignment
-- for the known bootstrap platform-admin accounts.
-- Safe to run on any environment — affects only the bootstrap emails.
-- ============================================================

UPDATE users
SET    super_admin_role_id = NULL
WHERE  role  = 'SUPER_ADMIN'
  AND  email IN ('superadmin@test.com', 'superadmin@innovax.tech');
