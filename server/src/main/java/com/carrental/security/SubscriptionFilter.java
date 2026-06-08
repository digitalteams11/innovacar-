package com.carrental.security;

import com.carrental.entity.Role;
import com.carrental.entity.User;
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

/**
 * Filter that intercepts authenticated requests to ensure the tenant's
 * subscription is still valid (active and not expired).
 *
 * <p>Excludes public endpoints (/api/auth/**) and the subscription renewal API
 * (/api/subscriptions/**) so admins can actually renew their expired subscriptions.
 *
 * <p>SUPER_ADMIN users bypass subscription checks entirely.
 */
@Component
public class SubscriptionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Skip subscription check for public routes and the subscription API itself
        if (path.startsWith("/api/auth/") || path.startsWith("/api/subscriptions/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Check if user is authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {

            // 3. SUPER_ADMIN bypasses subscription checks
            if (user.getRole() == Role.SUPER_ADMIN) {
                filterChain.doFilter(request, response);
                return;
            }

            // 4. Check subscription validity
            if (!user.getTenant().isSubscriptionValid()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Tenant subscription is inactive or expired. Please renew your subscription to restore access.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
