package com.carrental.service;

import com.carrental.dto.rbac.*;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Backing service for {@code RoleManagementController} (/api/roles, /api/users/{id}/access).
 * A "roleId" throughout this class is either {@code "SYSTEM:<Role name>"} or
 * {@code "CUSTOM:<id>"} — the opaque identifier the frontend role list uses so
 * one UI can address both kinds of role uniformly.
 */
@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RolePermissionService rolePermissionService;
    private final RolePermissionRepository rolePermissionRepository;
    private final CustomRoleRepository customRoleRepository;
    private final CustomRolePermissionRepository customRolePermissionRepository;
    private final PermissionDefinitionRepository definitionRepository;
    private final UserRepository userRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final TenantRepository tenantRepository;
    private final RoleAuditLogService roleAuditLogService;
    private final PermissionResolutionService permissionResolutionService;

    private static final String SYSTEM_PREFIX = "SYSTEM:";
    private static final String CUSTOM_PREFIX = "CUSTOM:";

    // ── Role listing ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RoleSummaryDto> listRoles() {
        Long tenantId = requireTenantId();
        List<RoleSummaryDto> result = new ArrayList<>();

        for (Role role : rolePermissionService.configurableRoles()) {
            if (role == Role.CUSTOM) continue; // CUSTOM is a placeholder system value, not itself an assignable "role card"
            long count = userRepository.findAllByTenantIdAndRole(tenantId, role).size();
            result.add(RoleSummaryDto.builder()
                    .roleId(SYSTEM_PREFIX + role.name())
                    .code(role.name())
                    .name(role.name())
                    .type("SYSTEM_ROLE")
                    .description(null)
                    .color(null)
                    .icon(null)
                    .userCount(count)
                    .editable(true)
                    .deletable(false)
                    .build());
        }

        for (CustomRole customRole : customRoleRepository.findAllByTenantId(tenantId)) {
            long count = userRepository.countByTenantIdAndCustomRoleId(tenantId, customRole.getId());
            result.add(RoleSummaryDto.builder()
                    .roleId(CUSTOM_PREFIX + customRole.getId())
                    .code(customRole.getCode())
                    .name(customRole.getName())
                    .type("CUSTOM_ROLE")
                    .description(customRole.getDescription())
                    .color(customRole.getColor())
                    .icon(customRole.getIcon())
                    .userCount(count)
                    .editable(true)
                    .deletable(count == 0)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RoleDetailDto getRole(String roleId) {
        Long tenantId = requireTenantId();
        List<PermissionDefinition> definitions = currentCatalogDefinitions();

        if (roleId.startsWith(SYSTEM_PREFIX)) {
            Role role = parseSystemRole(roleId);
            Set<String> enabled = rolePermissionRepository.findAllByTenantIdAndRole(tenantId, role).stream()
                    .filter(rp -> Boolean.TRUE.equals(rp.getEnabled()))
                    .map(RolePermission::getPermissionCode)
                    .collect(Collectors.toSet());
            return RoleDetailDto.builder()
                    .roleId(roleId).code(role.name()).name(role.name())
                    .description(null).type("SYSTEM_ROLE").editable(true)
                    .permissions(toPermissionStates(definitions, enabled))
                    .build();
        }

        CustomRole customRole = findCustomRoleOrThrow(roleId, tenantId);
        Set<String> enabled = customRolePermissionRepository.findAllByCustomRoleId(customRole.getId()).stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .map(CustomRolePermission::getPermissionCode)
                .collect(Collectors.toSet());
        return RoleDetailDto.builder()
                .roleId(roleId).code(customRole.getCode()).name(customRole.getName())
                .description(customRole.getDescription()).type("CUSTOM_ROLE").editable(true)
                .permissions(toPermissionStates(definitions, enabled))
                .build();
    }

    // ── Custom role CRUD ─────────────────────────────────────────────────────

    @Transactional
    public RoleDetailDto createCustomRole(CreateCustomRoleRequest request, HttpServletRequest httpRequest) {
        Long tenantId = requireTenantId();
        String code = request.getCode() == null ? null : request.getCode().trim().toUpperCase().replace(' ', '_');
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Role code is required");
        if (customRoleRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException("A role with code '" + code + "' already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        CustomRole customRole = customRoleRepository.save(CustomRole.builder()
                .tenant(tenant).code(code).name(request.getName() != null ? request.getName() : code)
                .description(request.getDescription()).baseTemplate(request.getBaseTemplate())
                .color(request.getColor()).icon(request.getIcon()).build());

        Set<String> enabled = new LinkedHashSet<>(
                request.getEnabledPermissionCodes() != null ? request.getEnabledPermissionCodes() : List.of());
        enabled = PermissionDependencyResolver.expandWithDependencies(enabled);
        for (String code0 : enabled) {
            customRolePermissionRepository.save(CustomRolePermission.builder()
                    .customRole(customRole).permissionCode(code0).enabled(true).build());
        }

        roleAuditLogService.logRoleCreated("CUSTOM_ROLE", code, customRole.getId(), null, httpRequest);
        return getRole(CUSTOM_PREFIX + customRole.getId());
    }

    @Transactional
    public void deleteCustomRole(String roleId, HttpServletRequest httpRequest) {
        Long tenantId = requireTenantId();
        CustomRole customRole = findCustomRoleOrThrow(roleId, tenantId);
        long assigned = userRepository.countByTenantIdAndCustomRoleId(tenantId, customRole.getId());
        if (assigned > 0) {
            throw new IllegalStateException("Cannot delete a role assigned to " + assigned + " user(s) — reassign them first");
        }
        customRolePermissionRepository.deleteAllByCustomRoleId(customRole.getId());
        customRoleRepository.delete(customRole);
        roleAuditLogService.logRoleDeleted("CUSTOM_ROLE", customRole.getCode(), customRole.getId(), null, httpRequest);
    }

    // ── Bulk permission update (system or custom role) ──────────────────────

    @Transactional
    public RoleDetailDto updateRolePermissions(String roleId, UpdateRolePermissionsRequest request, HttpServletRequest httpRequest) {
        Long tenantId = requireTenantId();
        Set<String> requested = new LinkedHashSet<>(
                request.getEnabledPermissionCodes() != null ? request.getEnabledPermissionCodes() : List.of());
        Set<String> expanded = PermissionDependencyResolver.expandWithDependencies(requested);

        if (roleId.startsWith(SYSTEM_PREFIX)) {
            Role role = parseSystemRole(roleId);
            Set<String> before = rolePermissionRepository.findAllByTenantIdAndRole(tenantId, role).stream()
                    .filter(rp -> Boolean.TRUE.equals(rp.getEnabled()))
                    .map(RolePermission::getPermissionCode)
                    .collect(Collectors.toSet());
            for (String code : currentCatalogDefinitions().stream().map(PermissionDefinition::getCode).toList()) {
                boolean shouldBeEnabled = expanded.contains(code);
                boolean wasEnabled = before.contains(code);
                if (shouldBeEnabled == wasEnabled) continue;
                rolePermissionService.setPermission(role, code, shouldBeEnabled);
                roleAuditLogService.logRoleUpdated("SYSTEM_ROLE", role.name(), null, code, wasEnabled, shouldBeEnabled, httpRequest);
            }
            return getRole(roleId);
        }

        CustomRole customRole = findCustomRoleOrThrow(roleId, tenantId);
        Map<String, CustomRolePermission> existingByCode = customRolePermissionRepository
                .findAllByCustomRoleId(customRole.getId()).stream()
                .collect(Collectors.toMap(CustomRolePermission::getPermissionCode, p -> p));
        for (String code : currentCatalogDefinitions().stream().map(PermissionDefinition::getCode).toList()) {
            boolean shouldBeEnabled = expanded.contains(code);
            CustomRolePermission existing = existingByCode.get(code);
            boolean wasEnabled = existing != null && Boolean.TRUE.equals(existing.getEnabled());
            if (shouldBeEnabled == wasEnabled) continue;
            if (existing == null) {
                customRolePermissionRepository.save(CustomRolePermission.builder()
                        .customRole(customRole).permissionCode(code).enabled(shouldBeEnabled).build());
            } else {
                existing.setEnabled(shouldBeEnabled);
                customRolePermissionRepository.save(existing);
            }
            roleAuditLogService.logRoleUpdated("CUSTOM_ROLE", customRole.getCode(), customRole.getId(), code, wasEnabled, shouldBeEnabled, httpRequest);
        }
        return getRole(roleId);
    }

    // ── Per-user access ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> effectivePermissionsFor(Long userId) {
        User user = requireTenantUser(userId);
        return permissionResolutionService.toResponse(permissionResolutionService.effectiveAccessFor(user));
    }

    @Transactional
    public void changeUserRole(Long userId, UserRoleChangeRequest request, HttpServletRequest httpRequest) {
        Long tenantId = requireTenantId();
        User user = requireTenantUser(userId);
        String oldDescription = user.getCustomRole() != null
                ? "CUSTOM:" + user.getCustomRole().getId() : user.getRole().name();

        if (request.getCustomRoleId() != null) {
            CustomRole customRole = customRoleRepository.findByIdAndTenantId(request.getCustomRoleId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Custom role not found"));
            user.setCustomRole(customRole);
            user.setRole(Role.CUSTOM);
        } else if (request.getRoleCode() != null) {
            Role role;
            try {
                role = Role.valueOf(request.getRoleCode());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown role code: " + request.getRoleCode());
            }
            if (role == Role.SUPER_ADMIN) {
                throw new AccessDeniedException("SUPER_ADMIN cannot be assigned from the agency page");
            }
            user.setRole(role);
            user.setCustomRole(null);
        } else {
            throw new IllegalArgumentException("Either roleCode or customRoleId must be provided");
        }

        userRepository.save(user);
        String newDescription = request.getCustomRoleId() != null
                ? "CUSTOM:" + request.getCustomRoleId() : request.getRoleCode();
        roleAuditLogService.logUserRoleChanged(userId, oldDescription, newDescription, httpRequest);
    }

    @Transactional
    public void addUserOverride(Long userId, PermissionOverrideRequest request, HttpServletRequest httpRequest) {
        User user = requireTenantUser(userId);
        PermissionOverrideType type;
        try {
            type = PermissionOverrideType.valueOf(request.getOverrideType());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("overrideType must be GRANT or DENY");
        }
        UserPermissionOverride override = overrideRepository.findByUserIdAndPermissionCode(userId, request.getPermissionCode())
                .orElseGet(() -> UserPermissionOverride.builder().user(user).permissionCode(request.getPermissionCode()).build());
        override.setOverrideType(type);
        override.setReason(request.getReason());
        override.setUpdatedAt(java.time.LocalDateTime.now());
        overrideRepository.save(override);
        roleAuditLogService.logUserOverrideAdded(userId, request.getPermissionCode(), type.name(), request.getReason(), httpRequest);
    }

    @Transactional
    public void removeUserOverride(Long userId, String permissionCode, HttpServletRequest httpRequest) {
        requireTenantUser(userId);
        overrideRepository.deleteByUserIdAndPermissionCode(userId, permissionCode);
        roleAuditLogService.logUserOverrideRemoved(userId, permissionCode, httpRequest);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<PermissionStateDto> toPermissionStates(List<PermissionDefinition> definitions, Set<String> enabled) {
        return definitions.stream()
                .sorted(Comparator.comparing(d -> d.getSortOrder() == null ? 0 : d.getSortOrder()))
                .map(d -> PermissionStateDto.builder()
                        .code(d.getCode()).module(d.getModule()).resource(d.getResource()).action(d.getAction())
                        .labelKey(d.getLabelKey()).descriptionKey(d.getDescriptionKey())
                        .riskLevel(d.getRiskLevel() == null ? null : d.getRiskLevel().name())
                        .dependencies(d.getDependencies() == null || d.getDependencies().isBlank()
                                ? List.of() : Arrays.asList(d.getDependencies().split(",")))
                        .enabled(enabled.contains(d.getCode()))
                        .isNew(d.getRegisteredAt() != null && d.getRegisteredAt().isAfter(java.time.LocalDateTime.now().minusDays(14)))
                        .build())
                .toList();
    }

    private List<PermissionDefinition> currentCatalogDefinitions() {
        return definitionRepository.findAllByActiveTrueAndDeprecatedFalse();
    }

    private CustomRole findCustomRoleOrThrow(String roleId, Long tenantId) {
        Long id = parseCustomRoleId(roleId);
        return customRoleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom role not found"));
    }

    private Role parseSystemRole(String roleId) {
        try {
            return Role.valueOf(roleId.substring(SYSTEM_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Unknown role: " + roleId);
        }
    }

    private Long parseCustomRoleId(String roleId) {
        if (!roleId.startsWith(CUSTOM_PREFIX)) throw new ResourceNotFoundException("Unknown role id: " + roleId);
        try {
            return Long.parseLong(roleId.substring(CUSTOM_PREFIX.length()));
        } catch (NumberFormatException ex) {
            throw new ResourceNotFoundException("Unknown role id: " + roleId);
        }
    }

    private User requireTenantUser(Long userId) {
        Long tenantId = requireTenantId();
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) throw new AccessDeniedException("No tenant context for this request");
        return tenantId;
    }
}
