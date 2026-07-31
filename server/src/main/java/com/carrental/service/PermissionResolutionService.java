package com.carrental.service;

import com.carrental.entity.PermissionOverrideType;
import com.carrental.entity.User;
import com.carrental.entity.UserPermissionOverride;
import com.carrental.repository.UserPermissionOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side companion to {@link RolePermissionService#hasResolvedPermission}
 * (which remains the single enforcement path behind {@code has()} so every
 * existing {@code @PreAuthorize("@rolePermissionService.has('...')")} keeps
 * working unchanged). This service answers the richer question the "Access &
 * Role" tab and effective-permission preview need: not just "can this user do
 * X" but "which permissions come from their role vs. an explicit override" —
 * spec: "show additional / restricted permissions", "preview effective
 * access".
 */
@Service
@RequiredArgsConstructor
public class PermissionResolutionService {

    private final RolePermissionService rolePermissionService;
    private final UserPermissionOverrideRepository overrideRepository;

    public record EffectiveAccess(
            String roleCode,
            List<String> effectivePermissions,
            List<String> additionalGrants,
            List<String> restrictedDenials) {}

    @Transactional(readOnly = true)
    public EffectiveAccess effectiveAccessFor(User user) {
        List<String> effective = rolePermissionService.permissionsFor(user);
        List<UserPermissionOverride> overrides = user.getId() == null
                ? List.of() : overrideRepository.findAllByUserId(user.getId());

        List<String> grants = overrides.stream()
                .filter(o -> o.getOverrideType() == PermissionOverrideType.GRANT)
                .map(UserPermissionOverride::getPermissionCode)
                .toList();
        List<String> denials = overrides.stream()
                .filter(o -> o.getOverrideType() == PermissionOverrideType.DENY)
                .map(UserPermissionOverride::getPermissionCode)
                .toList();

        return new EffectiveAccess(
                user.getRole() == null ? null : user.getRole().name(),
                effective, grants, denials);
    }

    /** True if this permission is granted purely by an explicit user override, not by the role/custom-role itself. */
    @Transactional(readOnly = true)
    public boolean isGrantedByOverrideOnly(User user, String permissionCode) {
        if (user.getId() == null) return false;
        return overrideRepository.findByUserIdAndPermissionCode(user.getId(), permissionCode)
                .map(o -> o.getOverrideType() == PermissionOverrideType.GRANT)
                .orElse(false);
    }

    public Map<String, Object> toResponse(EffectiveAccess access) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roleCode", access.roleCode());
        body.put("effectivePermissions", access.effectivePermissions());
        body.put("additionalGrants", access.additionalGrants());
        body.put("restrictedDenials", access.restrictedDenials());
        return body;
    }
}
