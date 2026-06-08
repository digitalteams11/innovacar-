package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.PermissionDefinitionRepository;
import com.carrental.repository.RolePermissionRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service("rolePermissionService")
@RequiredArgsConstructor
public class RolePermissionService {
    private final PermissionDefinitionRepository definitionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TenantRepository tenantRepository;

    private static final String[][] DEFINITIONS = {
            {"VIEW_VEHICLES", "View Vehicles", "Fleet"}, {"CREATE_VEHICLE", "Create Vehicle", "Fleet"},
            {"EDIT_VEHICLE", "Edit Vehicle", "Fleet"}, {"DELETE_VEHICLE", "Delete Vehicle", "Fleet"},
            {"VIEW_CLIENTS", "View Clients", "Clients"}, {"CREATE_CLIENT", "Create Client", "Clients"},
            {"EDIT_CLIENT", "Edit Client", "Clients"}, {"DELETE_CLIENT", "Delete Client", "Clients"},
            {"VIEW_RESERVATIONS", "View Reservations", "Reservations"}, {"CREATE_RESERVATION", "Create Reservation", "Reservations"},
            {"EDIT_RESERVATION", "Edit Reservation", "Reservations"}, {"CANCEL_RESERVATION", "Cancel Reservation", "Reservations"},
            {"VIEW_CONTRACTS", "View Contracts", "Contracts"}, {"CREATE_CONTRACT", "Create Contract", "Contracts"},
            {"SIGN_CONTRACT", "Sign Contract", "Contracts"}, {"COMPLETE_CONTRACT", "Complete Contract", "Contracts"},
            {"VIEW_PAYMENTS", "View Payments", "Finance"}, {"RECORD_PAYMENT", "Record Payment", "Finance"},
            {"VIEW_INVOICES", "View Invoices", "Finance"}, {"MANAGE_INVOICES", "Manage Invoices", "Finance"},
            {"VIEW_REPORTS", "View Reports", "Analytics"}, {"GPS_ACCESS", "GPS Access", "Fleet"},
            {"VIEW_MAINTENANCE", "View Maintenance", "Fleet"},
            {"MANAGE_MAINTENANCE", "Manage Maintenance", "Fleet"},
            {"MANAGE_EMPLOYEES", "Manage Employees", "Administration"}, {"MANAGE_SETTINGS", "Manage Settings", "Administration"}
    };

    @Transactional
    public void ensureTenantDefaults(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        for (String[] row : DEFINITIONS) {
            definitionRepository.findByCode(row[0]).orElseGet(() -> definitionRepository.save(
                    PermissionDefinition.builder().code(row[0]).name(row[1]).category(row[2]).build()));
        }
        for (Role role : List.of(Role.AGENCY_OWNER, Role.ADMIN, Role.MANAGER, Role.EMPLOYEE,
                Role.ACCOUNTANT, Role.RECEPTIONIST, Role.VIEWER, Role.AGENT)) {
            for (String[] row : DEFINITIONS) {
                String code = row[0];
                rolePermissionRepository.findByTenantIdAndRoleAndPermissionCode(tenantId, role, code)
                        .orElseGet(() -> rolePermissionRepository.save(RolePermission.builder()
                                .tenant(tenant).role(role).permissionCode(code)
                                .enabled(defaultEnabled(role, code)).build()));
            }
        }
    }

    @Transactional
    public Map<String, Object> currentAccess() {
        User user = currentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return Map.of("role", user.getRole(), "permissions",
                    definitionRepository.findAll().stream().map(PermissionDefinition::getCode).toList());
        }
        ensureTenantDefaults(user.getTenant().getId());
        return Map.of(
                "role", user.getRole(),
                "permissions", rolePermissionRepository.findAllByTenantIdAndRole(
                                user.getTenant().getId(), user.getRole()).stream()
                        .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                        .map(RolePermission::getPermissionCode).toList());
    }

    @Transactional
    public boolean has(String permissionCode) {
        User user = currentUser();
        if (user.getRole() == Role.SUPER_ADMIN) return true;
        ensureTenantDefaults(user.getTenant().getId());
        return rolePermissionRepository.existsByTenantIdAndRoleAndPermissionCodeAndEnabledTrue(
                user.getTenant().getId(), user.getRole(), permissionCode);
    }

    @Transactional
    public RolePermission setPermission(Role role, String permissionCode, boolean enabled) {
        Long tenantId = TenantContext.getCurrentTenantId();
        ensureTenantDefaults(tenantId);
        definitionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
        RolePermission permission = rolePermissionRepository
                .findByTenantIdAndRoleAndPermissionCode(tenantId, role, permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Role permission not found"));
        permission.setEnabled(enabled);
        return rolePermissionRepository.save(permission);
    }

    @Transactional
    public Map<String, Object> matrix() {
        ensureTenantDefaults(TenantContext.getCurrentTenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("definitions", definitionRepository.findAll());
        Map<String, List<RolePermission>> roles = new LinkedHashMap<>();
        for (Role role : List.of(Role.AGENCY_OWNER, Role.ADMIN, Role.MANAGER, Role.EMPLOYEE,
                Role.ACCOUNTANT, Role.RECEPTIONIST, Role.VIEWER, Role.AGENT)) {
            roles.put(role.name(), rolePermissionRepository.findAllByTenantIdAndRole(
                    TenantContext.getCurrentTenantId(), role));
        }
        result.put("roles", roles);
        return result;
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User user)) throw new IllegalStateException("Authenticated user not found");
        return user;
    }

    private boolean defaultEnabled(Role role, String code) {
        if (role == Role.AGENCY_OWNER || role == Role.ADMIN) return true;
        if (role == Role.MANAGER) return !code.equals("DELETE_VEHICLE") && !code.equals("DELETE_CLIENT");
        if (role == Role.ACCOUNTANT) return Set.of("VIEW_CLIENTS", "VIEW_CONTRACTS", "VIEW_PAYMENTS",
                "RECORD_PAYMENT", "VIEW_INVOICES", "MANAGE_INVOICES", "VIEW_REPORTS").contains(code);
        if (role == Role.RECEPTIONIST) return Set.of("VIEW_VEHICLES", "VIEW_CLIENTS", "CREATE_CLIENT",
                "EDIT_CLIENT", "VIEW_RESERVATIONS", "CREATE_RESERVATION", "EDIT_RESERVATION",
                "VIEW_CONTRACTS", "CREATE_CONTRACT", "VIEW_MAINTENANCE").contains(code);
        if (role == Role.VIEWER) return code.startsWith("VIEW_");
        return Set.of("VIEW_VEHICLES", "VIEW_CLIENTS", "CREATE_CLIENT", "EDIT_CLIENT",
                "VIEW_RESERVATIONS", "CREATE_RESERVATION", "EDIT_RESERVATION",
                "VIEW_CONTRACTS", "CREATE_CONTRACT", "SIGN_CONTRACT", "VIEW_MAINTENANCE").contains(code);
    }
}
