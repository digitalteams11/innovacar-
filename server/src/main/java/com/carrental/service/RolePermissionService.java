package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.exception.AdminLockoutException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.PermissionDefinitionRepository;
import com.carrental.repository.RolePermissionRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service("rolePermissionService")
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {
    private final PermissionDefinitionRepository definitionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TenantRepository tenantRepository;
    private final PermissionSyncService permissionSyncService;
    private final com.carrental.repository.UserPermissionOverrideRepository overrideRepository;
    private final com.carrental.repository.CustomRolePermissionRepository customRolePermissionRepository;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private RolePermissionService self;

    private static final Map<String, Set<String>> ALIASES = buildAliases();

    /** Every permission code this codebase currently recognizes (modern + legacy) — used by RoleManagementService when seeding/updating a custom role's full permission set. */
    public List<String> allCatalogCodes() {
        return permissionSyncService.allCodes();
    }

    public void ensureTenantDefaults(Long tenantId) {
        // PermissionSyncService.sync() is the real source of truth for permission_definitions
        // metadata now (see PermissionCatalog) — it's a fast no-op after the first call this
        // process lifetime, so calling it defensively here (as this method always has been
        // called before every matrix read/write) costs nothing once warmed up.
        permissionSyncService.sync();
        if (tenantId == null) return;
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        for (Role role : configurableRoles()) {
            for (String code : permissionSyncService.allCodes()) self.ensureRolePermissionExists(tenant, role, code);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureRolePermissionExists(Tenant tenant, Role role, String permissionCode) {
        if (!rolePermissionRepository.findAllByTenantIdAndRoleAndPermissionCode(tenant.getId(), role, permissionCode).isEmpty()) return;
        try {
            rolePermissionRepository.save(RolePermission.builder()
                    .tenant(tenant).role(role).permissionCode(permissionCode)
                    .enabled(defaultEnabled(role, permissionCode)).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Role permission '{}:{}:{}' was already created concurrently", tenant.getId(), role, permissionCode);
        }
    }

    @Transactional
    public Map<String, Object> currentAccess() {
        User user = currentUser();
        List<String> permissions = permissionsFor(user);
        return Map.of(
                "role", user.getRole(),
                "roleCode", user.getRole() == null ? null : user.getRole().name(),
                "isAgencyAdmin", user.getRole() == Role.ADMIN || user.getRole() == Role.AGENCY_OWNER,
                "isEmployee", user.getRole() != Role.ADMIN && user.getRole() != Role.AGENCY_OWNER && user.getRole() != Role.SUPER_ADMIN,
                "permissions", permissions);
    }

    @Transactional(readOnly = true)
    public List<String> permissionsFor(User user) {
        if (user == null || user.getRole() == null) return List.of();
        return permissionSyncService.allCodes().stream()
                .filter(code -> hasResolvedPermission(user, code))
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean has(String permissionCode) {
        return hasResolvedPermission(currentUser(), permissionCode);
    }

    @Transactional
    public RolePermission setPermission(Role role, String permissionCode, boolean enabled) {
        Long tenantId = TenantContext.getCurrentTenantId();
        User currentUser = currentUser();
        log.debug("[ROLE_ACCESS_UPDATE_DEBUG] currentUserId={} currentUserRole={} agencyId={} targetRole={} permissionKey={} enabled={} requestPayload=[enabled={}]",
                currentUser.getId(), currentUser.getRole(), tenantId, role, permissionCode, enabled, enabled);

        if (role == Role.SUPER_ADMIN) {
            log.warn("[ROLE_ACCESS_UPDATE_DEBUG] rejected: SUPER_ADMIN cannot be edited from the agency page");
            throw new AccessDeniedException("SUPER_ADMIN permissions cannot be edited from the agency page.");
        }

        // The repair step below is best-effort: if it fails for an unrelated reason
        // (e.g. a transient DB hiccup), the toggle can still succeed as long as the
        // target row already exists — mirrors the same resilience matrix() already has.
        try {
            ensureTenantDefaults(tenantId);
        } catch (Exception ex) {
            log.warn("[ROLE_ACCESS_UPDATE_DEBUG] ensureTenantDefaults failed for agencyId={}: {}", tenantId, ex.getMessage());
        }

        boolean permissionExists = !definitionRepository.findAllByCode(permissionCode).isEmpty();
        if (!permissionExists) {
            log.warn("[ROLE_ACCESS_UPDATE_DEBUG] errorCode=PERMISSION_NOT_FOUND permissionKey={}", permissionCode);
            throw new ResourceNotFoundException("Permission not found");
        }

        List<RolePermission> existing = rolePermissionRepository
                .findAllByTenantIdAndRoleAndPermissionCode(tenantId, role, permissionCode);
        boolean roleExists = !existing.isEmpty();
        log.debug("[ROLE_ACCESS_UPDATE_DEBUG] roleExists={} permissionExists={}", roleExists, permissionExists);
        if (!roleExists) {
            log.warn("[ROLE_ACCESS_UPDATE_DEBUG] errorCode=ROLE_PERMISSION_NOT_FOUND targetRole={} permissionKey={}", role, permissionCode);
            throw new ResourceNotFoundException("Role permission not found");
        }

        if (!enabled) {
            guardAgainstAdminLockout(tenantId, role, permissionCode);
        }

        RolePermission permission = existing.get(0);
        boolean oldValue = Boolean.TRUE.equals(permission.getEnabled());
        permission.setEnabled(enabled);
        RolePermission saved = rolePermissionRepository.save(permission);
        log.debug("[ROLE_ACCESS_UPDATE_DEBUG] currentUserId={} agencyId={} targetRole={} permissionKey={} oldValue={} newValue={} saved=true",
                currentUser.getId(), tenantId, role, permissionCode, oldValue, enabled);
        return saved;
    }

    private static final Set<Role> LOCKOUT_PROTECTED_ROLES = Set.of(Role.ADMIN, Role.AGENCY_OWNER);

    /**
     * Blocks disabling a permission that would leave an agency with no way to
     * manage its own role access — either by directly removing ROLE_ACCESS_MANAGE
     * from an admin-tier role, or by disabling the last remaining enabled
     * permission for that role.
     */
    private void guardAgainstAdminLockout(Long tenantId, Role role, String permissionCode) {
        if (!LOCKOUT_PROTECTED_ROLES.contains(role)) return;

        String canonicalCode = canonical(permissionCode);
        if ("ROLE_ACCESS_MANAGE".equals(canonicalCode)) {
            throw new AdminLockoutException(
                    "You cannot remove this permission because it would lock administrators out.");
        }

        long remainingEnabled = rolePermissionRepository.findAllByTenantIdAndRole(tenantId, role).stream()
                .filter(p -> !p.getPermissionCode().equals(permissionCode))
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .count();
        if (remainingEnabled == 0) {
            throw new AdminLockoutException(
                    "You cannot remove this permission because it would lock administrators out.");
        }
    }

    /**
     * The new backend permission catalog, DTO-shaped (spec section 27: "Do not
     * expose entity graphs directly") — full module/resource/action/risk/
     * dependency metadata for the role-editor UI. Only the current, non-legacy
     * permission set is returned; deprecated alias codes (still active for
     * backward-compatible grants) are intentionally omitted here since the new
     * UI only ever displays/edits the modern codes.
     */
    @Transactional
    public List<com.carrental.dto.rbac.PermissionCatalogEntryDto> catalog() {
        try { ensureTenantDefaults(TenantContext.getCurrentTenantId()); } catch (Exception ex) {
            log.warn("Permission catalog default repair failed: {}", ex.getMessage());
        }
        return definitionRepository.findAllByActiveTrueAndDeprecatedFalse().stream()
                .map(com.carrental.dto.rbac.PermissionCatalogEntryDto::from)
                .sorted(Comparator.comparingInt(com.carrental.dto.rbac.PermissionCatalogEntryDto::getSortOrder))
                .toList();
    }

    @Transactional
    public Map<String, Object> matrix() {
        Long tenantId = TenantContext.getCurrentTenantId();
        try { ensureTenantDefaults(tenantId); } catch (Exception ex) {
            log.warn("Permission matrix default repair failed for tenant [{}]: {}", tenantId, ex.getMessage());
        }
        List<Map<String, Object>> definitions = definitionRepository.findAll().stream().map(definition -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", definition.getId());
            item.put("code", definition.getCode());
            item.put("name", definition.getName());
            item.put("description", definition.getDescription());
            item.put("category", definition.getCategory());
            return item;
        }).toList();
        Map<String, List<Map<String, Object>>> roles = new LinkedHashMap<>();
        for (Role role : configurableRoles()) {
            roles.put(role.name(), tenantId == null ? List.of() : rolePermissionRepository.findAllByTenantIdAndRole(tenantId, role).stream()
                    .map(this::permissionRow).toList());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("definitions", definitions);
        data.put("roles", roles);
        data.put("permissions", definitions);
        data.put("matrix", roles);
        Map<String, Object> result = new LinkedHashMap<>(data);
        result.put("success", true);
        result.put("message", "Permission matrix loaded successfully");
        result.put("data", data);
        return result;
    }

    @Transactional
    public Map<String, Object> saveMatrix(Map<String, List<String>> roleToEnabledCodes) {
        Long tenantId = TenantContext.getCurrentTenantId();
        try {
            ensureTenantDefaults(tenantId);
        } catch (Exception ex) {
            log.warn("[ROLE_ACCESS_UPDATE_DEBUG] ensureTenantDefaults failed during bulk save for agencyId={}: {}", tenantId, ex.getMessage());
        }
        if (tenantId != null && roleToEnabledCodes != null) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            for (Map.Entry<String, List<String>> entry : roleToEnabledCodes.entrySet()) {
                Role role;
                try { role = Role.valueOf(entry.getKey()); } catch (IllegalArgumentException ex) { continue; }
                Set<String> enabledCodes = new HashSet<>(entry.getValue() == null ? List.of() : entry.getValue());
                for (String code : permissionSyncService.allCodes()) {
                    self.ensureRolePermissionExists(tenant, role, code);
                    List<RolePermission> existing = rolePermissionRepository
                            .findAllByTenantIdAndRoleAndPermissionCode(tenantId, role, code);
                    if (!existing.isEmpty()) {
                        RolePermission permission = existing.get(0);
                        permission.setEnabled(enabledCodes.contains(code));
                        rolePermissionRepository.save(permission);
                    }
                }
            }
        }
        return matrix();
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User user)) throw new IllegalStateException("Authenticated user not found");
        return user;
    }

    /**
     * Resolution order (spec section 2):
     * 1. Super Admin / agency-owner-tier bypass (ADMIN and AGENCY_OWNER have
     *    always had unconditional access in this product — unchanged here).
     * 2. Deactivated permission (removed from the catalog) — hard deny,
     *    regardless of any stored grant (spec: "deleted/inactive permission
     *    no longer grants access").
     * 3. Explicit user-level DENY override — always wins over the role.
     * 4. Explicit user-level GRANT override — grants even if the role denies.
     * 5. If the user has an agency-defined custom role assigned, that role's
     *    own configured permission (default deny if unset for that role).
     * 6. Otherwise the system role's own configured/default permission.
     */
    private boolean hasResolvedPermission(User user, String permissionCode) {
        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.AGENCY_OWNER || user.getRole() == Role.ADMIN) return true;
        if (!self.isActiveCode(permissionCode)) return false;

        Optional<Boolean> override = resolveOverride(user, permissionCode);
        if (override.isPresent()) return override.get();

        if (user.getCustomRole() != null) {
            for (String code : equivalentCodes(permissionCode)) {
                Optional<Boolean> configured = customRolePermissionRepository
                        .findByCustomRoleIdAndPermissionCode(user.getCustomRole().getId(), code)
                        .map(CustomRolePermission::getEnabled);
                if (configured.isPresent()) return Boolean.TRUE.equals(configured.get());
            }
            return false;
        }

        if (user.getTenant() == null) return defaultEnabled(user.getRole(), permissionCode);
        Long tenantId = user.getTenant().getId();
        for (String code : equivalentCodes(permissionCode)) {
            Optional<Boolean> configured = rolePermissionRepository
                    .findAllByTenantIdAndRoleAndPermissionCode(tenantId, user.getRole(), code)
                    .stream().findFirst().map(RolePermission::getEnabled);
            if (configured.isPresent()) return Boolean.TRUE.equals(configured.get());
        }
        return defaultEnabled(user.getRole(), permissionCode);
    }

    /** True if the code (or a still-active alias of it) is a currently-recognized, non-deactivated permission. */
    @Transactional(readOnly = true)
    public boolean isActiveCode(String permissionCode) {
        for (String code : equivalentCodes(permissionCode)) {
            List<PermissionDefinition> defs = definitionRepository.findAllByCode(code);
            if (defs.stream().anyMatch(d -> Boolean.TRUE.equals(d.getActive()))) return true;
        }
        return false;
    }

    private Optional<Boolean> resolveOverride(User user, String permissionCode) {
        if (user.getId() == null) return Optional.empty();
        for (String code : equivalentCodes(permissionCode)) {
            Optional<Boolean> override = overrideRepository.findByUserIdAndPermissionCode(user.getId(), code)
                    .map(o -> o.getOverrideType() == PermissionOverrideType.GRANT);
            if (override.isPresent()) return override;
        }
        return Optional.empty();
    }

    private boolean defaultEnabled(Role role, String code) {
        String c = canonical(code);
        if (role == Role.AGENCY_OWNER || role == Role.ADMIN) return true;
        if (role == Role.MANAGER) return Set.of(
                "DASHBOARD_VIEW", "VEHICLE_VIEW", "VEHICLE_CREATE", "VEHICLE_UPDATE", "CLIENT_VIEW", "CLIENT_CREATE", "CLIENT_UPDATE",
                "RESERVATION_VIEW", "RESERVATION_CREATE", "RESERVATION_UPDATE", "RESERVATION_CANCEL", "CONTRACT_VIEW", "CONTRACT_CREATE",
                "CONTRACT_UPDATE", "CONTRACT_EXPORT_PDF", "CONTRACT_QR_SIGNATURE", "CONTRACT_INSPECTION_MEDIA", "PAYMENT_VIEW",
                "PAYMENT_CREATE", "PAYMENT_STATS_VIEW", "INVOICE_VIEW", "REPORT_VIEW", "GPS_VIEW", "EMPLOYEE_VIEW").contains(c);
        if (role == Role.AGENT || role == Role.RECEPTIONIST || role == Role.EMPLOYEE) return Set.of(
                "DASHBOARD_VIEW", "VEHICLE_VIEW", "CLIENT_VIEW", "CLIENT_CREATE", "RESERVATION_VIEW", "RESERVATION_CREATE",
                "RESERVATION_UPDATE", "CONTRACT_VIEW", "CONTRACT_CREATE", "CONTRACT_EXPORT_PDF", "CONTRACT_QR_SIGNATURE",
                "CONTRACT_INSPECTION_MEDIA", "PAYMENT_VIEW", "PAYMENT_CREATE").contains(c);
        if (role == Role.ACCOUNTANT) return Set.of(
                "DASHBOARD_VIEW", "CLIENT_VIEW", "CONTRACT_VIEW", "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_UPDATE",
                "PAYMENT_STATS_VIEW", "INVOICE_VIEW", "INVOICE_EXPORT", "REPORT_VIEW", "REPORT_FINANCIAL").contains(c);
        if (role == Role.FLEET_MANAGER) return Set.of(
                "DASHBOARD_VIEW", "VEHICLE_VIEW", "VEHICLE_CREATE", "VEHICLE_UPDATE", "VEHICLE_MAINTENANCE_MANAGE",
                "RESERVATION_VIEW", "CONTRACT_VIEW", "GPS_VIEW", "GPS_ALERTS_VIEW").contains(c);
        if (role == Role.DRIVER) return Set.of(
                "DASHBOARD_VIEW", "RESERVATION_VIEW", "CONTRACT_VIEW", "CONTRACT_INSPECTION_MEDIA", "VEHICLE_VIEW").contains(c);
        if (role == Role.VIEWER) return Set.of(
                "DASHBOARD_VIEW", "VEHICLE_VIEW", "CLIENT_VIEW", "RESERVATION_VIEW", "CONTRACT_VIEW", "REPORT_VIEW").contains(c);
        return false;
    }

    public List<Role> configurableRoles() {
        return List.of(Role.ADMIN, Role.MANAGER, Role.AGENT, Role.ACCOUNTANT, Role.FLEET_MANAGER,
                Role.DRIVER, Role.VIEWER, Role.RECEPTIONIST, Role.EMPLOYEE, Role.CUSTOM);
    }

    private Map<String, Object> permissionRow(RolePermission permission) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", permission.getId());
        row.put("role", permission.getRole() == null ? null : permission.getRole().name());
        row.put("permissionCode", permission.getPermissionCode());
        row.put("enabled", Boolean.TRUE.equals(permission.getEnabled()));
        return row;
    }

    private static Map<String, Set<String>> buildAliases() {
        Map<String, String> pairs = Map.ofEntries(
                Map.entry("VIEW_VEHICLES", "VEHICLE_VIEW"), Map.entry("CREATE_VEHICLE", "VEHICLE_CREATE"),
                Map.entry("EDIT_VEHICLE", "VEHICLE_UPDATE"), Map.entry("DELETE_VEHICLE", "VEHICLE_DELETE"),
                Map.entry("VIEW_CLIENTS", "CLIENT_VIEW"), Map.entry("CREATE_CLIENT", "CLIENT_CREATE"),
                Map.entry("EDIT_CLIENT", "CLIENT_UPDATE"), Map.entry("DELETE_CLIENT", "CLIENT_DELETE"),
                Map.entry("VIEW_RESERVATIONS", "RESERVATION_VIEW"), Map.entry("CREATE_RESERVATION", "RESERVATION_CREATE"),
                Map.entry("EDIT_RESERVATION", "RESERVATION_UPDATE"), Map.entry("CANCEL_RESERVATION", "RESERVATION_CANCEL"),
                Map.entry("VIEW_CONTRACTS", "CONTRACT_VIEW"), Map.entry("CREATE_CONTRACT", "CONTRACT_CREATE"),
                Map.entry("EDIT_CONTRACT", "CONTRACT_UPDATE"), Map.entry("DELETE_CONTRACT", "CONTRACT_DELETE"),
                Map.entry("SIGN_CONTRACT", "CONTRACT_QR_SIGNATURE"), Map.entry("COMPLETE_CONTRACT", "CONTRACT_UPDATE"),
                Map.entry("VIEW_PAYMENTS", "PAYMENT_VIEW"), Map.entry("RECORD_PAYMENT", "PAYMENT_CREATE"),
                Map.entry("VIEW_INVOICES", "INVOICE_VIEW"), Map.entry("MANAGE_INVOICES", "INVOICE_EXPORT"),
                Map.entry("VIEW_REPORTS", "REPORT_VIEW"), Map.entry("GPS_ACCESS", "GPS_VIEW"),
                Map.entry("MANAGE_GPS", "GPS_SETTINGS"), Map.entry("GPS_SETTINGS_VIEW", "GPS_VIEW"),
                Map.entry("GPS_SETTINGS_UPDATE", "GPS_SETTINGS"), Map.entry("VIEW_MAINTENANCE", "VEHICLE_VIEW"),
                Map.entry("MANAGE_MAINTENANCE", "VEHICLE_MAINTENANCE_MANAGE"), Map.entry("MANAGE_EMPLOYEES", "EMPLOYEE_CREATE"),
                Map.entry("MANAGE_SETTINGS", "AGENCY_SETTINGS_UPDATE"));
        Map<String, Set<String>> aliases = new HashMap<>();
        pairs.forEach((legacy, modern) -> {
            aliases.computeIfAbsent(legacy, ignored -> new LinkedHashSet<>()).add(modern);
            aliases.computeIfAbsent(modern, ignored -> new LinkedHashSet<>()).add(legacy);
        });
        return aliases;
    }

    private static Set<String> equivalentCodes(String code) {
        return Stream.concat(Stream.of(code), ALIASES.getOrDefault(code, Set.of()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String canonical(String code) {
        if (code == null) return "";
        return switch (code) {
            case "VIEW_VEHICLES" -> "VEHICLE_VIEW";
            case "CREATE_VEHICLE" -> "VEHICLE_CREATE";
            case "EDIT_VEHICLE" -> "VEHICLE_UPDATE";
            case "DELETE_VEHICLE" -> "VEHICLE_DELETE";
            case "VIEW_CLIENTS" -> "CLIENT_VIEW";
            case "CREATE_CLIENT" -> "CLIENT_CREATE";
            case "EDIT_CLIENT" -> "CLIENT_UPDATE";
            case "DELETE_CLIENT" -> "CLIENT_DELETE";
            case "VIEW_RESERVATIONS" -> "RESERVATION_VIEW";
            case "CREATE_RESERVATION" -> "RESERVATION_CREATE";
            case "EDIT_RESERVATION" -> "RESERVATION_UPDATE";
            case "CANCEL_RESERVATION" -> "RESERVATION_CANCEL";
            case "VIEW_CONTRACTS" -> "CONTRACT_VIEW";
            case "CREATE_CONTRACT" -> "CONTRACT_CREATE";
            case "EDIT_CONTRACT" -> "CONTRACT_UPDATE";
            case "DELETE_CONTRACT" -> "CONTRACT_DELETE";
            case "SIGN_CONTRACT" -> "CONTRACT_QR_SIGNATURE";
            case "COMPLETE_CONTRACT" -> "CONTRACT_UPDATE";
            case "VIEW_PAYMENTS" -> "PAYMENT_VIEW";
            case "RECORD_PAYMENT" -> "PAYMENT_CREATE";
            case "VIEW_INVOICES" -> "INVOICE_VIEW";
            case "MANAGE_INVOICES" -> "INVOICE_EXPORT";
            case "VIEW_REPORTS" -> "REPORT_VIEW";
            case "GPS_ACCESS", "GPS_SETTINGS_VIEW" -> "GPS_VIEW";
            case "MANAGE_GPS", "GPS_SETTINGS_UPDATE" -> "GPS_SETTINGS";
            case "VIEW_MAINTENANCE" -> "VEHICLE_VIEW";
            case "MANAGE_MAINTENANCE" -> "VEHICLE_MAINTENANCE_MANAGE";
            case "MANAGE_EMPLOYEES" -> "EMPLOYEE_CREATE";
            case "MANAGE_SETTINGS" -> "AGENCY_SETTINGS_UPDATE";
            default -> code;
        };
    }
}