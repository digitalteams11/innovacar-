package com.carrental.controller;

import com.carrental.dto.superadmin.datareset.DataResetRequest;
import com.carrental.entity.Role;
import com.carrental.entity.SuperAdminRole;
import com.carrental.entity.User;
import com.carrental.service.SuperAdminDataResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Super Admin "Data Reset Center" — destructive agency/client/platform data
 * resets, gated behind email verification + 2FA + password + an exact
 * per-agency confirmation code. Restricted to SUPER_ADMIN by
 * {@code /api/super-admin/**} in SecurityConfig; {@link SuperAdminDataResetService}
 * additionally requires the SUPER_OWNER sub-role.
 */
@Slf4j
@RestController
@RequestMapping("/api/super-admin/data-reset")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminDataResetController {

    private final SuperAdminDataResetService dataResetService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal User currentUser) {
        // Pre-compute isSuperOwner before calling the service so the fallback has it
        // even if the service throws (e.g. a transient DB error on backup/audit queries).
        boolean isSuperOwner = resolveIsSuperOwner(currentUser);
        log.debug("[DATA_RESET_ACCESS_DEBUG] status() user={} role={} superAdminRoleCode={} isSuperOwner={}",
                currentUser != null ? currentUser.getEmail() : "null",
                currentUser != null ? currentUser.getRole() : "null",
                currentUser != null && currentUser.getSuperAdminRole() != null
                        ? currentUser.getSuperAdminRole().getCode() : "null",
                isSuperOwner);
        try {
            return ResponseEntity.ok(wrap(dataResetService.status(currentUser), "Data reset status loaded."));
        } catch (Exception ex) {
            log.warn("[DATA_RESET_ACCESS_DEBUG] status() service call failed for user={} — using fallback",
                    currentUser != null ? currentUser.getEmail() : "unknown", ex);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("isSuperOwner", isSuperOwner);
            fallback.put("emailVerified", currentUser != null && Boolean.TRUE.equals(currentUser.getEmailVerified()));
            fallback.put("twoFactorEnabled", currentUser != null && Boolean.TRUE.equals(currentUser.getTwoFactorEnabled()));
            fallback.put("environment", "unknown");
            fallback.put("backupAvailable", false);
            fallback.put("lastResetAction", null);
            fallback.put("warning", "Data reset service fallback was used.");
            return ResponseEntity.ok(wrap(fallback, "Data reset status loaded with fallback."));
        }
    }

    private boolean resolveIsSuperOwner(User user) {
        if (user == null || user.getRole() != Role.SUPER_ADMIN) return false;
        try {
            SuperAdminRole subRole = user.getSuperAdminRole();
            return subRole == null || "SUPER_OWNER".equalsIgnoreCase(subRole.getCode());
        } catch (Exception ex) {
            log.warn("[DATA_RESET_ACCESS_DEBUG] Could not load superAdminRole for user={}: {}",
                    user.getEmail(), ex.getMessage());
            return false;
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @Valid @RequestBody DataResetRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(wrap(dataResetService.preview(request, currentUser), "Reset preview generated."));
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @Valid @RequestBody DataResetRequest request,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = dataResetService.execute(request, currentUser, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(wrap(result, "Data reset completed successfully."));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> auditLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(wrap(dataResetService.auditLogs(limit), "Audit logs loaded."));
    }

    private Map<String, Object> wrap(Object data, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);
        return body;
    }
}
