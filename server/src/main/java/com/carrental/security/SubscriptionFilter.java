package com.carrental.security;

import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filter that intercepts authenticated requests to ensure the tenant's
 * account/subscription is still in good standing before allowing any
 * mutation. A Super-Admin block/suspend/deactivate always wins over plan
 * state; a lapsed subscription on its own only blocks writes, never reads.
 *
 * <p>Excludes public endpoints (/api/auth/**) and the subscription/billing/
 * support/profile APIs an account must always be able to reach even while
 * blocked (renew billing, contact support, log out, update own profile).
 *
 * <p>SUPER_ADMIN users bypass these checks entirely.
 */
@Component
public class SubscriptionFilter extends OncePerRequestFilter {
    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Always reachable regardless of account/subscription state — even fully blocked agencies need these. */
    private static final List<String> ALWAYS_ALLOWED_PREFIXES = List.of(
            "/api/auth/",
            "/api/public/",
            "/api/subscriptions/",
            "/api/support/",
            "/api/webhooks/",
            "/api/client-errors"
    );
    private static final Set<String> ALWAYS_ALLOWED_PATHS = Set.of(
            "/api/health",
            "/actuator/health",
            "/api/me",
            "/api/agency",
            "/api/users/me/basic-profile"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isAlwaysAllowed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {

            if (user.getRole() == Role.SUPER_ADMIN) {
                filterChain.doFilter(request, response);
                return;
            }

            if (user.getTenant() == null) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "TENANT_REQUIRED",
                        "Agency account is not linked to a tenant.", null);
                return;
            }

            Tenant tenant = user.getTenant();
            boolean readOnly = READ_ONLY_METHODS.contains(request.getMethod());

            if (tenant.isAccountBlocked()) {
                if (readOnly) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String status = tenant.getStatus() == null ? "SUSPENDED" : tenant.getStatus().toUpperCase();
                String errorCode = "BLOCKED".equals(status) ? "AGENCY_BLOCKED" : "AGENCY_SUSPENDED";
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("agencyStatus", status);
                data.put("subscriptionStatus", status);
                data.put("allowedPages", List.of("billing", "support", "profile"));
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, errorCode,
                        "Your agency account is suspended. Please contact Innovax Technologies or update your subscription.",
                        data);
                return;
            }

            if (!tenant.isSubscriptionValid()) {
                if (readOnly) {
                    filterChain.doFilter(request, response);
                    return;
                }
                boolean expired = tenant.getSubscriptionEndDate() != null
                        && LocalDate.now().isAfter(tenant.getSubscriptionEndDate());
                String errorCode = expired ? "SUBSCRIPTION_EXPIRED" : "SUBSCRIPTION_SUSPENDED";
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("subscriptionStatus", expired ? "EXPIRED" : "SUSPENDED");
                writeJson(response, HttpServletResponse.SC_PAYMENT_REQUIRED, errorCode,
                        "Your subscription is expired. Please renew your plan to continue.", data);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAlwaysAllowed(String path) {
        if (ALWAYS_ALLOWED_PATHS.contains(path)) return true;
        return ALWAYS_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void writeJson(HttpServletResponse response, int status, String errorCode, String message, Map<String, Object> data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errorCode", errorCode);
        body.put("data", data);
        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}
