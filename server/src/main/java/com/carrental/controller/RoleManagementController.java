package com.carrental.controller;

import com.carrental.dto.rbac.*;
import com.carrental.entity.RoleAuditLog;
import com.carrental.service.RoleAuditLogService;
import com.carrental.service.RoleManagementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The new dynamic-RBAC management surface. Kept separate from the existing
 * {@code /api/permissions} controller (untouched, still backs the legacy
 * matrix and every {@code @PreAuthorize("@rolePermissionService.has(...)")}
 * check) — this controller adds role CRUD, custom roles, and per-user
 * overrides on top of it. Every endpoint is tenant-scoped from
 * {@code TenantContext}; no tenant/agency id is ever accepted from the client.
 */
@RestController
@RequiredArgsConstructor
public class RoleManagementController {

    private final RoleManagementService roleManagementService;
    private final RoleAuditLogService roleAuditLogService;

    @GetMapping("/api/roles")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<List<RoleSummaryDto>> listRoles() {
        return ResponseEntity.ok(roleManagementService.listRoles());
    }

    @GetMapping("/api/roles/{roleId}")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<RoleDetailDto> getRole(@PathVariable String roleId) {
        return ResponseEntity.ok(roleManagementService.getRole(roleId));
    }

    @PostMapping("/api/roles")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<RoleDetailDto> createRole(@RequestBody CreateCustomRoleRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleManagementService.createCustomRole(request, httpRequest));
    }

    @PutMapping("/api/roles/{roleId}/permissions")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<RoleDetailDto> updatePermissions(
            @PathVariable String roleId, @RequestBody UpdateRolePermissionsRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleManagementService.updateRolePermissions(roleId, request, httpRequest));
    }

    @DeleteMapping("/api/roles/{roleId}")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<Void> deleteRole(@PathVariable String roleId, HttpServletRequest httpRequest) {
        roleManagementService.deleteCustomRole(roleId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/roles/audit")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<Page<RoleAuditLog>> listAudit(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Long tenantId = com.carrental.security.TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(roleAuditLogService.listForTenant(tenantId, PageRequest.of(page, size)));
    }

    @GetMapping("/api/users/{userId}/effective-permissions")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<Map<String, Object>> effectivePermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(roleManagementService.effectivePermissionsFor(userId));
    }

    @PutMapping("/api/users/{userId}/role")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<Void> changeUserRole(
            @PathVariable Long userId, @RequestBody UserRoleChangeRequest request, HttpServletRequest httpRequest) {
        roleManagementService.changeUserRole(userId, request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/users/{userId}/permission-overrides")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<Void> addOverride(
            @PathVariable Long userId, @RequestBody PermissionOverrideRequest request, HttpServletRequest httpRequest) {
        roleManagementService.addUserOverride(userId, request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/users/{userId}/permission-overrides/{permissionCode}")
    @PreAuthorize("@rolePermissionService.has('ROLE_ACCESS_MANAGE')")
    public ResponseEntity<Void> removeOverride(
            @PathVariable Long userId, @PathVariable String permissionCode, HttpServletRequest httpRequest) {
        roleManagementService.removeUserOverride(userId, permissionCode, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
