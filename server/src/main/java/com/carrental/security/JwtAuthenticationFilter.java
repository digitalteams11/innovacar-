package com.carrental.security;

import com.carrental.entity.User;
import com.carrental.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts every HTTP request and, when a valid JWT is present,
 * populates the {@link SecurityContextHolder} with the authenticated principal.
 *
 * <p>Multi-tenancy enforcement: the tenantId embedded in the JWT is stored in a
 * thread-local {@link TenantContext} so that downstream service/repository
 * calls can automatically scope queries to the correct tenant.
 *
 * <p>Token expiry detection: when an access token is expired, the response
 * includes a {@code Token-Expired: true} header so the client can trigger
 * a refresh token flow instead of immediately redirecting to login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository   userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                if (jwtTokenProvider.validateAccessToken(token)) {
                    Claims claims   = jwtTokenProvider.parseClaims(token);
                    String email    = claims.getSubject();
                    Long   tenantId = claims.get("tenantId", Long.class);

                    // Store tenant context for downstream use
                    TenantContext.setCurrentTenantId(tenantId);

                    // Load user scoped to the tenant extracted from the token
                    User user = userRepository.findByEmailAndTenantId(email, tenantId)
                            .orElse(null);

                    if (user != null) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (ExpiredJwtException e) {
                // Token is expired — signal to frontend for refresh
                response.setHeader("Token-Expired", "true");
                log.debug("Expired JWT from {}: {}", request.getRequestURI(), e.getMessage());
            } catch (Exception ex) {
                log.error("Could not set user authentication: {}", ex.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear thread-local to prevent leakage between requests
            TenantContext.clear();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        // Check Authorization header first
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // Fallback: check query parameter (needed for SSE EventSource which can't set headers)
        String param = request.getParameter("token");
        if (StringUtils.hasText(param)) {
            return param;
        }
        return null;
    }
}
