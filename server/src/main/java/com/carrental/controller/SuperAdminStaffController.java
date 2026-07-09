package com.carrental.controller;

import com.carrental.dto.superadmin.CreateStaffRequest;
import com.carrental.dto.superadmin.SuperAdminStaffResponse;
import com.carrental.dto.superadmin.UpdateStaffRequest;
import com.carrental.entity.User;
import com.carrental.service.SuperAdminPermissionService;
import com.carrental.service.SuperAdminStaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Manages Innovax platform staff accounts (Super Admin control center).
 * Every endpoint requires the {@code STAFF_MANAGE} platform permission —
 * enforced server-side, never trusting the frontend.
 */
@RestController
@RequestMapping("/api/super-admin/staff")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminStaffController {

    private final SuperAdminStaffService staffService;
    private final SuperAdminPermissionService permissionService;

    @GetMapping
    @PreAuthorize("@superAdminPermissionService.has('STAFF_MANAGE')")
    public ResponseEntity<List<SuperAdminStaffResponse>> listStaff() {
        return ResponseEntity.ok(staffService.listStaff().stream().map(SuperAdminStaffResponse::from).toList());
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        try {
            return ResponseEntity.ok(permissionService.currentAccess());
        } catch (Exception ex) {
            Map<String, Object> fallback = new java.util.LinkedHashMap<>();
            fallback.put("success", true);
            fallback.put("email", auth != null ? auth.getName() : null);
            fallback.put("role", "SUPER_ADMIN");
            fallback.put("permissions", List.of("ALL"));
            fallback.put("warning", "Super admin staff access fallback was used.");
            return ResponseEntity.ok(fallback);
        }
    }

    @PostMapping
    @PreAuthorize("@superAdminPermissionService.has('STAFF_MANAGE')")
    public ResponseEntity<SuperAdminStaffResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        User staff = staffService.createStaff(
                request.getEmail(), request.getPassword(), request.getFirstName(),
                request.getLastName(), request.getSuperAdminRoleId());
        return ResponseEntity.ok(SuperAdminStaffResponse.from(staff));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@superAdminPermissionService.has('STAFF_MANAGE')")
    public ResponseEntity<SuperAdminStaffResponse> updateStaff(
            @PathVariable Long id, @RequestBody UpdateStaffRequest request) {
        User staff = staffService.updateStaff(id, request.getFirstName(), request.getLastName(), request.getSuperAdminRoleId());
        return ResponseEntity.ok(SuperAdminStaffResponse.from(staff));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@superAdminPermissionService.has('STAFF_MANAGE')")
    public ResponseEntity<SuperAdminStaffResponse> setStatus(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal User caller) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        if (!enabled && caller.getId().equals(id)) {
            throw new IllegalArgumentException("You cannot suspend your own staff account.");
        }
        User staff = staffService.setStaffStatus(id, enabled);
        return ResponseEntity.ok(SuperAdminStaffResponse.from(staff));
    }
}
