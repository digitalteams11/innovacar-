-- ============================================================
-- V81 — Dynamic RBAC permission registry.
--
-- permission_definitions today is populated purely from
-- RolePermissionService.DEFINITIONS (a hardcoded String[][]) and only carries
-- code/name/description/category. This migration adds the metadata columns
-- needed for the new backend permission catalog (module/resource/action/risk/
-- dependencies/sort order) plus the active/deprecated flags that let the
-- startup PermissionSyncService retire a removed permission WITHOUT deleting
-- its row or touching any tenant's existing role_permissions grants for it
-- (see PermissionSyncService — deactivate, never delete).
--
-- Also adds the tables for: agency-defined custom roles (layered ALONGSIDE
-- the existing Role enum, not replacing it — every current @PreAuthorize
-- check and Role.ADMIN/.. comparison across ~40 controllers keeps working
-- unchanged), per-user permission overrides (grant/deny on top of a role),
-- and a permission-change audit log.
-- ============================================================

-- ── 1. Rich metadata on permission_definitions ───────────────────────────────

ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS module VARCHAR(60);
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS resource VARCHAR(60);
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS action VARCHAR(60);
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS label_key VARCHAR(150);
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS description_key VARCHAR(150);
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20);
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS dependencies VARCHAR(500);
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS sort_order INTEGER;
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS active BOOLEAN;
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS deprecated BOOLEAN;
-- Set only on a deprecated legacy/alias code (e.g. VIEW_VEHICLES) -> the modern
-- canonical code it mirrors (VEHICLE_VIEW). Used by the role editor to hide
-- legacy codes from the "current" permission list while keeping them active.
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS alias_of VARCHAR(100);
-- Set only when PermissionSyncService actually inserts a brand-new code (never
-- touched on update) — lets the role editor show a "New" badge on a
-- permission introduced by a recent feature until reviewed by an admin.
ALTER TABLE permission_definitions ADD COLUMN IF NOT EXISTS registered_at TIMESTAMP;
UPDATE permission_definitions SET registered_at = NOW() - INTERVAL '365 days' WHERE registered_at IS NULL;

UPDATE permission_definitions SET risk_level = 'NORMAL' WHERE risk_level IS NULL;
UPDATE permission_definitions SET sort_order = 0 WHERE sort_order IS NULL;
-- Existing rows must stay fully functional (they may back live role_permissions
-- grants) until the startup sync explicitly reclassifies them as deprecated
-- (legacy alias, still active) or inactive (truly removed, no longer in the
-- catalog at all) — see PermissionSyncService.
UPDATE permission_definitions SET active = TRUE WHERE active IS NULL;
UPDATE permission_definitions SET deprecated = FALSE WHERE deprecated IS NULL;

ALTER TABLE permission_definitions ALTER COLUMN risk_level SET DEFAULT 'NORMAL';
ALTER TABLE permission_definitions ALTER COLUMN sort_order SET DEFAULT 0;
ALTER TABLE permission_definitions ALTER COLUMN active SET DEFAULT TRUE;
ALTER TABLE permission_definitions ALTER COLUMN deprecated SET DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_permission_definitions_active ON permission_definitions(active);
CREATE INDEX IF NOT EXISTS idx_permission_definitions_module ON permission_definitions(module);

-- ── 2. Agency-defined custom roles (additive — Role enum is untouched) ───────

CREATE TABLE IF NOT EXISTS custom_roles (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code              VARCHAR(60)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    description       VARCHAR(500),
    base_template     VARCHAR(30),  -- which system Role this was cloned from, for the "reset to default" action
    color             VARCHAR(20),
    icon              VARCHAR(40),
    created_by_user_id BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_custom_role_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS custom_role_permissions (
    id              BIGSERIAL PRIMARY KEY,
    custom_role_id  BIGINT      NOT NULL REFERENCES custom_roles(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL,
    enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_custom_role_permission UNIQUE (custom_role_id, permission_code)
);

CREATE INDEX IF NOT EXISTS idx_custom_role_permissions_role ON custom_role_permissions(custom_role_id);

-- A user may be assigned a custom role instead of (in addition to, for legacy
-- compatibility) a system Role — nullable, and User.role keeps its NOT NULL
-- system-role value (defaulted to CUSTOM) so every existing hasRole()/Role.X
-- comparison in the codebase keeps resolving to something sane even for a
-- custom-role user.
ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_role_id BIGINT REFERENCES custom_roles(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_users_custom_role ON users(custom_role_id);

-- ── 3. Per-user permission overrides (grant or deny on top of the role) ──────

CREATE TABLE IF NOT EXISTS user_permission_overrides (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_code  VARCHAR(100) NOT NULL,
    override_type    VARCHAR(10)  NOT NULL, -- GRANT | DENY
    reason           VARCHAR(500),
    created_by_user_id BIGINT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_permission_override UNIQUE (user_id, permission_code)
);

CREATE INDEX IF NOT EXISTS idx_user_permission_overrides_user ON user_permission_overrides(user_id);

-- ── 4. Audit log for every role/permission change ────────────────────────────

CREATE TABLE IF NOT EXISTS role_audit_logs (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT,
    action              VARCHAR(40)  NOT NULL, -- ROLE_CREATED | ROLE_UPDATED | ROLE_DELETED | ROLE_PERMISSION_CHANGED | USER_ROLE_CHANGED | USER_OVERRIDE_ADDED | USER_OVERRIDE_REMOVED
    role_type           VARCHAR(20),           -- SYSTEM_ROLE | CUSTOM_ROLE
    role_code           VARCHAR(60),
    custom_role_id       BIGINT,
    target_user_id       BIGINT,
    changed_by_user_id   BIGINT,
    permission_code      VARCHAR(100),
    old_value            VARCHAR(60),
    new_value            VARCHAR(60),
    reason                VARCHAR(500),
    ip_address            VARCHAR(64),
    user_agent            VARCHAR(255),
    created_at             TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_role_audit_logs_tenant ON role_audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_role_audit_logs_target_user ON role_audit_logs(target_user_id);
CREATE INDEX IF NOT EXISTS idx_role_audit_logs_created_at ON role_audit_logs(created_at);
