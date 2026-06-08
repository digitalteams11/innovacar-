package com.carrental.controller;

import com.carrental.entity.Role;
import com.carrental.entity.RolePermission;
import com.carrental.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class RolePermissionController {
    private final RolePermissionService permissionService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        return ResponseEntity.ok(permissionService.currentAccess());
    }

    @GetMapping("/matrix")
    @PreAuthorize("@rolePermissionService.has('MANAGE_EMPLOYEES')")
    public ResponseEntity<Map<String, Object>> matrix() {
        return ResponseEntity.ok(permissionService.matrix());
    }

    @PutMapping("/{role}/{permissionCode}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_EMPLOYEES')")
    public ResponseEntity<RolePermission> setPermission(
            @PathVariable Role role,
            @PathVariable String permissionCode,
            @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(permissionService.setPermission(
                role, permissionCode, Boolean.TRUE.equals(body.get("enabled"))));
    }
}
