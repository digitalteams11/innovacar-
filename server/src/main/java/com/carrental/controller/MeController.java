package com.carrental.controller;

import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
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
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("role", user.getRole());
        body.put("tenantId", user.getTenant() != null ? user.getTenant().getId() : null);
        body.put("tenantName", user.getTenant() != null ? user.getTenant().getName() : null);
        body.put("emailVerified", user.getEmailVerified());
        body.put("twoFactorEnabled", user.getTwoFactorEnabled());
        body.put("firstName", user.getFirstName());
        body.put("lastName", user.getLastName());
        body.put("phoneNumber", user.getPhoneNumber());
        body.put("jobTitle", user.getJobTitle());
        body.put("avatarUrl", user.getAvatarUrl());
        body.put("language", user.getLanguage());
        body.put("themeMode", user.getThemeMode());

        Tenant tenant = user.getTenant();
        if (tenant != null) {
            boolean blocked = tenant.isAccountBlocked();
            boolean subscriptionValid = tenant.isSubscriptionValid();
            String agencyStatus = tenant.getStatus();
            String subscriptionStatus = blocked ? agencyStatus
                    : subscriptionValid ? "ACTIVE"
                    : (tenant.getSubscriptionEndDate() != null
                            && java.time.LocalDate.now().isAfter(tenant.getSubscriptionEndDate()))
                            ? "EXPIRED" : "SUSPENDED";
            boolean canUsePlatform = subscriptionValid;

            Map<String, Object> accountAccess = new LinkedHashMap<>();
            accountAccess.put("canUsePlatform", canUsePlatform);
            accountAccess.put("canCreateContracts", canUsePlatform);
            accountAccess.put("canCreateReservations", canUsePlatform);
            accountAccess.put("canManageVehicles", canUsePlatform);
            accountAccess.put("canAccessBilling", true);
            accountAccess.put("canAccessSupport", true);
            accountAccess.put("blockedReason", canUsePlatform ? null
                    : blocked ? ("BLOCKED".equalsIgnoreCase(agencyStatus) ? "AGENCY_BLOCKED" : "AGENCY_SUSPENDED")
                    : "SUBSCRIPTION_" + subscriptionStatus);

            body.put("agencyId", tenant.getId());
            body.put("agencyStatus", agencyStatus);
            body.put("subscriptionStatus", subscriptionStatus);
            body.put("planCode", tenant.getPlanName());
            body.put("planName", tenant.getPlanName());
            body.put("features", List.of());
            body.put("limits", Map.of());
            body.put("accountAccess", accountAccess);
        }
        return ResponseEntity.ok(body);
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
