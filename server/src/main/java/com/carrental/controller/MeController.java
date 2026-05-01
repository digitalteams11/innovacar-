package com.carrental.controller;

import com.carrental.entity.User;
import com.carrental.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Example protected controller — demonstrates how every secured endpoint
 * automatically operates within the authenticated tenant's context.
 *
 * <p>These endpoints are protected by the JWT filter; the tenant isolation
 * is enforced at the service/repository layer via {@link TenantContext}.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    /**
     * Returns the current user's profile.
     * Any authenticated user (all roles) can call this.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> profile(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
            "id",         user.getId(),
            "email",      user.getEmail(),
            "role",       user.getRole(),
            "tenantId",   user.getTenant().getId(),
            "tenantName", user.getTenant().getName()
        ));
    }

    /**
     * Admin-only endpoint — protected with method-level security.
     */
    @GetMapping("/admin-dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminDashboard(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
            "message",  "Welcome to the admin dashboard",
            "tenant",   user.getTenant().getName(),
            "tenantId", TenantContext.getCurrentTenantId()
        ));
    }
}
