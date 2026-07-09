package com.carrental.controller;

import com.carrental.dto.superadmin.CreateRoleRequest;
import com.carrental.entity.SuperAdminRole;
import com.carrental.repository.SuperAdminRoleRepository;
import com.carrental.service.SuperAdminPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Manages platform staff roles and their permission grants (Super Admin
 * control center RBAC). See {@link SuperAdminPermissionService}.
 */
@RestController
@RequestMapping("/api/super-admin/roles")
@PreAuthorize("hasRole('SUPER_ADMIN') and @superAdminPermissionService.has('STAFF_MANAGE')")
@RequiredArgsConstructor
public class SuperAdminRoleController {

    private final SuperAdminRoleRepository roleRepository;
    private final SuperAdminPermissionService permissionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listRoles() {
        return ResponseEntity.ok(permissionService.matrix());
    }

    @PostMapping
    public ResponseEntity<SuperAdminRole> createRole(@Valid @RequestBody CreateRoleRequest request) {
        String code = request.getCode().trim().toUpperCase().replace(' ', '_');
        if (!roleRepository.findAllByCode(code).isEmpty()) {
            throw new IllegalArgumentException("A role with this code already exists");
        }
        try {
            SuperAdminRole role = roleRepository.save(SuperAdminRole.builder()
                    .code(code).label(request.getLabel()).description(request.getDescription())
                    .systemRole(false).build());
            // New custom roles start with no permissions granted — an explicit opt-in.
            permissionService.matrix(); // ensures definitions exist
            return ResponseEntity.ok(role);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("A role with this code already exists");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuperAdminRole> updateRole(@PathVariable Long id, @RequestBody CreateRoleRequest request) {
        SuperAdminRole role = roleRepository.findById(id)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Role not found"));
        if (request.getLabel() != null) role.setLabel(request.getLabel());
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        return ResponseEntity.ok(roleRepository.save(role));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<Map<String, Object>> updatePermissions(
            @PathVariable Long id, @RequestBody Map<String, Boolean> permissions) {
        permissions.forEach((code, enabled) -> permissionService.setPermission(id, code, Boolean.TRUE.equals(enabled)));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Role permissions updated successfully.",
                "data", permissionService.matrix()
        ));
    }
}
