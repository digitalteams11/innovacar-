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

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private RolePermissionService self;

    private static final String[][] DEFINITIONS = {
            {"DASHBOARD_VIEW", "View Dashboard", "Dashboard"},
            {"VEHICLE_VIEW", "View Vehicles", "Fleet"}, {"VEHICLE_CREATE", "Create Vehicle", "Fleet"},
            {"VEHICLE_UPDATE", "Update Vehicle", "Fleet"}, {"VEHICLE_DELETE", "Delete Vehicle", "Fleet"},
            {"VEHICLE_ARCHIVE", "Archive Vehicle", "Fleet"}, {"VEHICLE_MAINTENANCE_MANAGE", "Manage Vehicle Maintenance", "Fleet"},
            {"CLIENT_VIEW", "View Clients", "Clients"}, {"CLIENT_CREATE", "Create Client", "Clients"},
            {"CLIENT_UPDATE", "Update Client", "Clients"}, {"CLIENT_DELETE", "Delete Client", "Clients"},
            {"RESERVATION_VIEW", "View Reservations", "Reservations"}, {"RESERVATION_CREATE", "Create Reservation", "Reservations"},
            {"RESERVATION_UPDATE", "Update Reservation", "Reservations"}, {"RESERVATION_CANCEL", "Cancel Reservation", "Reservations"},
            {"RESERVATION_DELETE", "Delete Reservation", "Reservations"},
            {"CONTRACT_VIEW", "View Contracts", "Contracts"}, {"CONTRACT_CREATE", "Create Contract", "Contracts"},
            {"CONTRACT_UPDATE", "Update Contract", "Contracts"}, {"CONTRACT_DELETE", "Delete Contract", "Contracts"},
            {"CONTRACT_RESTORE", "Restore Contract", "Contracts"}, {"CONTRACT_PURGE", "Purge Contract", "Contracts"},
            {"CONTRACT_EXPORT_PDF", "Export Contract PDF", "Contracts"}, {"CONTRACT_QR_SIGNATURE", "QR Signature", "Contracts"},
            {"CONTRACT_INSPECTION_MEDIA", "Inspection Media", "Contracts"},
            {"PAYMENT_VIEW", "View Payments", "Finance"}, {"PAYMENT_CREATE", "Create Payment", "Finance"},
            {"PAYMENT_UPDATE", "Update Payment", "Finance"}, {"PAYMENT_REFUND", "Refund Payment", "Finance"},
            {"PAYMENT_STATS_VIEW", "View Payment Statistics", "Finance"},
            {"INVOICE_VIEW", "View Invoices", "Finance"}, {"INVOICE_EXPORT", "Export Invoices", "Finance"},
            {"REPORT_VIEW", "View Reports", "Analytics"}, {"REPORT_FINANCIAL", "Financial Reports", "Analytics"},
            {"REPORT_ADVANCED", "Advanced Reports", "Analytics"},
            {"GPS_VIEW", "View GPS", "Fleet"}, {"GPS_SETTINGS", "Manage GPS Settings", "Fleet"},
            {"GPS_ALERTS_VIEW", "View GPS Alerts", "Fleet"}, {"GPS_ALERTS_MANAGE", "Manage GPS Alerts", "Fleet"},
            {"EMPLOYEE_VIEW", "View Employees", "Administration"}, {"EMPLOYEE_CREATE", "Create Employee", "Administration"},
            {"EMPLOYEE_UPDATE", "Update Employee", "Administration"}, {"EMPLOYEE_DELETE", "Delete Employee", "Administration"},
            {"EMPLOYEE_RESET_PASSWORD", "Reset Employee Password", "Administration"},
            {"AGENCY_SETTINGS_VIEW", "View Agency Settings", "Administration"}, {"AGENCY_SETTINGS_UPDATE", "Update Agency Settings", "Administration"},
            {"ROLE_ACCESS_MANAGE", "Manage Role Access", "Administration"},
            {"SECURITY_VIEW", "View Security", "Security"}, {"SECURITY_MANAGE", "Manage Security", "Security"},
            {"VIEW_VEHICLES", "View Vehicles", "Fleet"}, {"CREATE_VEHICLE", "Create Vehicle", "Fleet"},
            {"EDIT_VEHICLE", "Edit Vehicle", "Fleet"}, {"DELETE_VEHICLE", "Delete Vehicle", "Fleet"},
            {"VIEW_CLIENTS", "View Clients", "Clients"}, {"CREATE_CLIENT", "Create Client", "Clients"},
            {"EDIT_CLIENT", "Edit Client", "Clients"}, {"DELETE_CLIENT", "Delete Client", "Clients"},
            {"VIEW_RESERVATIONS", "View Reservations", "Reservations"}, {"CREATE_RESERVATION", "Create Reservation", "Reservations"},
            {"EDIT_RESERVATION", "Edit Reservation", "Reservations"}, {"CANCEL_RESERVATION", "Cancel Reservation", "Reservations"},
            {"VIEW_CONTRACTS", "View Contracts", "Contracts"}, {"CREATE_CONTRACT", "Create Contract", "Contracts"},
            {"EDIT_CONTRACT", "Edit Contract", "Contracts"}, {"DELETE_CONTRACT", "Delete Contract", "Contracts"},
            {"SIGN_CONTRACT", "Sign Contract", "Contracts"}, {"COMPLETE_CONTRACT", "Complete Contract", "Contracts"},
            {"VIEW_PAYMENTS", "View Payments", "Finance"}, {"RECORD_PAYMENT", "Record Payment", "Finance"},
            {"VIEW_DEPOSITS", "View Deposits", "Finance"}, {"MANAGE_DEPOSITS", "Manage Deposits", "Finance"},
            {"VIEW_INVOICES", "View Invoices", "Finance"}, {"MANAGE_INVOICES", "Manage Invoices", "Finance"},
            {"VIEW_REPORTS", "View Reports", "Analytics"}, {"GPS_ACCESS", "GPS Access", "Fleet"},
            {"MANAGE_GPS", "Manage GPS Settings", "Fleet"}, {"GPS_SETTINGS_VIEW", "View GPS Settings", "Fleet"},
            {"GPS_SETTINGS_UPDATE", "Update GPS Settings", "Fleet"}, {"GPS_CREDENTIALS_DELETE", "Delete GPS Credentials", "Fleet"},
            {"GPS_TEST_CONNECTION", "Test GPS Connection", "Fleet"}, {"GPS_SYNC_DEVICES", "Sync GPS Devices", "Fleet"},
            {"VIEW_MAINTENANCE", "View Maintenance", "Fleet"}, {"MANAGE_MAINTENANCE", "Manage Maintenance", "Fleet"},
            {"MANAGE_EMPLOYEES", "Manage Employees", "Administration"},
            {"MANAGE_SETTINGS", "Manage Settings", "Administration"}
    };

    private static final Map<String, Set<String>> ALIASES = buildAliases();

    public void ensureTenantDefaults(Long tenantId) {
        for (String[] row : DEFINITIONS) self.ensureDefinitionExists(row[0], row[1], row[2]);
        if (tenantId == null) return;
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        for (Role role : configurableRoles()) {
            for (String[] row : DEFINITIONS) self.ensureRolePermissionExists(tenant, role, row[0]);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefinitionExists(String code, String name, String category) {
        if (!definitionRepository.findAllByCode(code).isEmpty()) return;
        try {
            definitionRepository.save(PermissionDefinition.builder().code(code).name(name).category(category).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Permission definition '{}' was already created concurrently", code);
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
        return Arrays.stream(DEFINITIONS)
                .map(row -> row[0])
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
        log.info("[ROLE_ACCESS_UPDATE_DEBUG] currentUserId={} currentUserRole={} agencyId={} targetRole={} permissionKey={} enabled={} requestPayload=[enabled={}]",
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
        log.info("[ROLE_ACCESS_UPDATE_DEBUG] roleExists={} permissionExists={}", roleExists, permissionExists);
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
        log.info("[ROLE_ACCESS_UPDATE_DEBUG] currentUserId={} agencyId={} targetRole={} permissionKey={} oldValue={} newValue={} saved=true",
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
                for (String[] row : DEFINITIONS) {
                    String code = row[0];
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

    private boolean hasResolvedPermission(User user, String permissionCode) {
        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.AGENCY_OWNER || user.getRole() == Role.ADMIN) return true;
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

    private List<Role> configurableRoles() {
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