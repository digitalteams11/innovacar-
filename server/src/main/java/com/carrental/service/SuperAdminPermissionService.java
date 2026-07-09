package com.carrental.service;

import com.carrental.entity.Role;
import com.carrental.entity.SuperAdminPermissionDefinition;
import com.carrental.entity.SuperAdminRole;
import com.carrental.entity.SuperAdminRolePermission;
import com.carrental.entity.User;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.SuperAdminPermissionDefinitionRepository;
import com.carrental.repository.SuperAdminRolePermissionRepository;
import com.carrental.repository.SuperAdminRoleRepository;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Platform-level (Innovax staff) RBAC — governs what a SUPER_ADMIN-role user
 * can do *inside* the Super Admin control center. Distinct from, and built on
 * top of, the existing tenant-scoped {@link RolePermissionService} which
 * governs agency staff. Global: there is no tenant scoping here.
 *
 * <p>Backward compatibility: a {@code SUPER_ADMIN} user with no
 * {@code superAdminRole} assigned (every account created before this system
 * existed) is treated as full-access — this system only ever *restricts*
 * staff that have been deliberately assigned a limited sub-role.
 */
@Service("superAdminPermissionService")
@RequiredArgsConstructor
@Slf4j
public class SuperAdminPermissionService {

    private final SuperAdminPermissionDefinitionRepository definitionRepository;
    private final SuperAdminRoleRepository roleRepository;
    private final SuperAdminRolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private SuperAdminPermissionService self;

    public static final String SUPER_OWNER = "SUPER_OWNER";

    /** {@code {code, name, category}} — the full platform permission catalog. */
    private static final String[][] DEFINITIONS = {
            {"AGENCIES_VIEW", "View Agencies", "Agencies"},
            {"AGENCIES_UPDATE", "Update Agencies", "Agencies"},
            {"AGENCIES_BLOCK", "Block / Suspend Agencies", "Agencies"},
            {"USERS_VIEW", "View Users", "Users"},
            {"USERS_BLOCK", "Block Users", "Users"},
            {"PLANS_VIEW", "View Plans", "Billing"},
            {"PLANS_UPDATE", "Update Plans", "Billing"},
            {"PROMO_CODES_MANAGE", "Manage Promo Codes", "Billing"},
            {"SUBSCRIPTIONS_MANAGE", "Manage Subscriptions", "Billing"},
            {"PAYMENTS_VIEW", "View Payments", "Billing"},
            {"PAYMENTS_UPDATE", "Update Payments", "Billing"},
            {"INVOICES_MANAGE", "Manage Invoices", "Billing"},
            {"SUPPORT_REPLY", "Reply to Support Tickets", "Support"},
            {"ANNOUNCEMENTS_MANAGE", "Manage Announcements", "Marketing"},
            {"EMAILS_MANAGE", "Manage Email Center", "Marketing"},
            {"SECURITY_VIEW", "View Security Center", "Security"},
            {"SECURITY_ACTIONS", "Take Security Actions", "Security"},
            {"AUDIT_LOGS_VIEW", "View Audit Logs", "Security"},
            {"PLATFORM_SETTINGS_UPDATE", "Update Platform Settings", "System"},
            {"PAYMENT_PROVIDERS_UPDATE", "Update Payment Providers", "System"},
            {"BACKUP_VIEW", "View Backups", "System"},
            {"STAFF_MANAGE", "Manage Super Admin Staff & Roles", "System"},
    };

    /** Default role -> permission codes, seeded once when each role is first created. */
    private static final Map<String, Set<String>> ROLE_DEFAULTS = Map.ofEntries(
            Map.entry("SUPER_OWNER", allCodes()),
            Map.entry("SUPER_ADMIN", allCodes()),
            Map.entry("MANAGER", Set.of("AGENCIES_VIEW", "AGENCIES_UPDATE", "USERS_VIEW",
                    "SUBSCRIPTIONS_MANAGE", "SUPPORT_REPLY", "ANNOUNCEMENTS_MANAGE")),
            Map.entry("FINANCE_MANAGER", Set.of("PLANS_VIEW", "PROMO_CODES_MANAGE",
                    "SUBSCRIPTIONS_MANAGE", "PAYMENTS_VIEW", "PAYMENTS_UPDATE", "INVOICES_MANAGE")),
            Map.entry("SUPPORT_AGENT", Set.of("AGENCIES_VIEW", "SUPPORT_REPLY")),
            Map.entry("SECURITY_OFFICER", Set.of("AGENCIES_VIEW", "AGENCIES_BLOCK", "USERS_VIEW",
                    "USERS_BLOCK", "SECURITY_VIEW", "SECURITY_ACTIONS", "AUDIT_LOGS_VIEW")),
            Map.entry("MARKETING_MANAGER", Set.of("ANNOUNCEMENTS_MANAGE", "EMAILS_MANAGE", "PROMO_CODES_MANAGE")),
            Map.entry("TECH_ADMIN", Set.of("BACKUP_VIEW", "SECURITY_VIEW", "AUDIT_LOGS_VIEW"))
    );

    private static Set<String> allCodes() {
        Set<String> codes = new HashSet<>();
        for (String[] row : DEFINITIONS) codes.add(row[0]);
        return codes;
    }

    /** {@code {code, label, description, systemRole}} — the seeded default staff roles. */
    private static final String[][] DEFAULT_ROLES = {
            {SUPER_OWNER, "Super Owner", "Full access to everything, including managing other staff."},
            {"SUPER_ADMIN", "Super Admin", "Full platform operations except critical owner-level changes."},
            {"MANAGER", "Manager", "Manages agencies, users, subscriptions, support, announcements."},
            {"FINANCE_MANAGER", "Finance Manager", "Manages subscriptions, payments, invoices, balances, promo codes."},
            {"SUPPORT_AGENT", "Support Agent", "Views agencies and replies to support tickets."},
            {"SECURITY_OFFICER", "Security Officer", "Security center, audit logs, sessions, suspicious activity, blocking."},
            {"MARKETING_MANAGER", "Marketing Manager", "Manages announcements, email campaigns, promo codes."},
            {"TECH_ADMIN", "Tech Admin", "Platform health, GPS provider status, logs, backups."},
    };

    /**
     * Idempotently seeds permission definitions + the 8 default staff roles
     * (and their default permission grants). Safe to call on every boot.
     */
    public void ensurePlatformDefaults() {
        for (String[] row : DEFINITIONS) {
            self.ensureDefinitionExists(row[0], row[1], row[2]);
        }
        for (String[] row : DEFAULT_ROLES) {
            SuperAdminRole role = self.ensureRoleExists(row[0], row[1], row[2]);
            Set<String> defaults = ROLE_DEFAULTS.getOrDefault(row[0], Set.of());
            for (String[] def : DEFINITIONS) {
                self.ensureRolePermissionExists(role, def[0], defaults.contains(def[0]));
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefinitionExists(String code, String name, String category) {
        if (!definitionRepository.findAllByCode(code).isEmpty()) return;
        try {
            definitionRepository.save(SuperAdminPermissionDefinition.builder()
                    .code(code).name(name).category(category).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Super admin permission definition '{}' already created concurrently", code);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SuperAdminRole ensureRoleExists(String code, String label, String description) {
        Optional<SuperAdminRole> existing = roleRepository.findFirstByCode(code);
        if (existing.isPresent()) return existing.get();
        try {
            return roleRepository.save(SuperAdminRole.builder()
                    .code(code).label(label).description(description).systemRole(true).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Super admin role '{}' already created concurrently", code);
            return roleRepository.findFirstByCode(code).orElseThrow();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureRolePermissionExists(SuperAdminRole role, String permissionCode, boolean defaultEnabled) {
        if (!rolePermissionRepository.findAllByRoleIdAndPermissionCode(role.getId(), permissionCode).isEmpty()) return;
        try {
            rolePermissionRepository.save(SuperAdminRolePermission.builder()
                    .role(role).permissionCode(permissionCode).enabled(defaultEnabled).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Super admin role permission '{}:{}' already created concurrently", role.getId(), permissionCode);
        }
    }

    /** Used by {@code @PreAuthorize("@superAdminPermissionService.has('CODE')")}. */
    @Transactional(readOnly = true)
    public boolean has(String permissionCode) {
        User user = currentUser();
        if (user.getRole() != Role.SUPER_ADMIN) return false;
        return hasResolvedPermission(user, permissionCode);
    }

    private boolean hasResolvedPermission(User user, String permissionCode) {
        SuperAdminRole role = user.getSuperAdminRole();
        if (role == null) return true; // legacy/unrestricted super admin
        return rolePermissionRepository.findAllByRoleIdAndPermissionCode(role.getId(), permissionCode)
                .stream()
                .findFirst()
                .map(SuperAdminRolePermission::getEnabled)
                .orElse(false);
    }

    @Transactional
    public Map<String, Object> currentAccess() {
        User user = currentUser();
        if (user.getRole() != Role.SUPER_ADMIN) {
            // Map.of() rejects null values (NullPointerException) — this branch
            // was the actual cause of the 500 on GET /api/super-admin/staff/me.
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("role", null);
            empty.put("permissions", List.of());
            return empty;
        }
        List<String> permissions = Arrays.stream(DEFINITIONS)
                .map(row -> row[0])
                .filter(code -> hasResolvedPermission(user, code))
                .toList();
        SuperAdminRole role = user.getSuperAdminRole();
        Map<String, Object> access = new LinkedHashMap<>();
        access.put("role", role != null ? role.getCode() : null);
        access.put("roleLabel", role != null ? role.getLabel() : "Unrestricted Super Admin");
        access.put("permissions", permissions);
        return access;
    }

    @Transactional
    public Map<String, Object> matrix() {
        ensurePlatformDefaults();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("definitions", definitionRepository.findAll());
        List<SuperAdminRole> roles = roleRepository.findAll();
        Map<String, List<SuperAdminRolePermission>> permissionsByRole = new LinkedHashMap<>();
        for (SuperAdminRole role : roles) {
            permissionsByRole.put(role.getCode(), rolePermissionRepository.findAllByRoleId(role.getId()));
        }
        data.put("roles", roles);
        data.put("matrix", permissionsByRole);
        return data;
    }

    @Transactional
    public SuperAdminRolePermission setPermission(Long roleId, String permissionCode, boolean enabled) {
        SuperAdminRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        if (SUPER_OWNER.equals(role.getCode()) && !enabled) {
            throw new IllegalArgumentException("The Super Owner role always has full access and cannot be restricted.");
        }
        if (definitionRepository.findAllByCode(permissionCode).isEmpty()) {
            throw new ResourceNotFoundException("Permission not found");
        }
        User currentUser = currentUser();
        if ("STAFF_MANAGE".equals(permissionCode) && !enabled
                && currentUser.getSuperAdminRole() != null
                && currentUser.getSuperAdminRole().getId().equals(roleId)) {
            throw new IllegalArgumentException("You cannot remove your own staff-management access.");
        }
        List<SuperAdminRolePermission> existing = rolePermissionRepository.findAllByRoleIdAndPermissionCode(roleId, permissionCode);
        SuperAdminRolePermission permission = existing.isEmpty()
                ? SuperAdminRolePermission.builder().role(role).permissionCode(permissionCode).build()
                : existing.get(0);
        permission.setEnabled(enabled);
        return rolePermissionRepository.save(permission);
    }

    public User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User user)) throw new IllegalStateException("Authenticated user not found");
        return user;
    }
}
