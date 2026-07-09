package com.carrental.security;

import com.carrental.entity.User;
import com.carrental.repository.UserRepository;
import com.carrental.repository.UserSessionRepository;
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
import java.time.LocalDateTime;

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
    private final UserSessionRepository userSessionRepository;
    private final AuthCookieService authCookieService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (path.startsWith("/api/public/")
                || path.equals("/api/health")
                || path.equals("/actuator/health")
                || path.equals("/api/client-errors")) return true;

        // Public auth routes — no JWT needed.
        // IMPORTANT: /api/auth/security-status, /api/auth/change-password,
        // and /api/auth/2fa/setup|confirm|disable|regenerate-codes are AUTHENTICATED
        // endpoints that require a valid JWT — they must NOT be skipped here.
        return path.equals("/api/auth/signup")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout")
                || path.equals("/api/auth/google")
                || path.startsWith("/api/auth/phone/")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/verify-reset-code")
                || path.equals("/api/auth/reset-password")
                || path.equals("/api/auth/verify-email")
                || path.equals("/api/auth/resend-verify")
                || path.equals("/api/auth/2fa/verify"); // second-leg login — uses challenge token, not JWT
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        TokenCandidate candidate = extractToken(request);
        String token = candidate.token();

        boolean hasCookieCredentials = candidate.fromCookie()
                || StringUtils.hasText(authCookieService.readRefreshToken(request));
        if (hasCookieCredentials && isUnsafeMethod(request.getMethod())
                && !hasCsrfGuardHeader(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"error\":\"CSRF_GUARD_MISSING\","
                            + "\"message\":\"Missing CSRF guard header.\"}");
            return;
        }

        if (StringUtils.hasText(token)) {
            try {
                if (jwtTokenProvider.validateAccessToken(token)) {
                    Claims claims   = jwtTokenProvider.parseClaims(token);
                    String email    = claims.getSubject();
                    Long   tenantId = claims.get("tenantId", Long.class);
                    Long   sessionId = claims.get("sessionId", Long.class);

                    // Store tenant context for downstream use
                    TenantContext.setCurrentTenantId(tenantId);

                    // Load user scoped to the tenant extracted from the token
                    User user = userRepository.findByEmailAndTenantId(email, tenantId)
                            .orElse(null);

                    boolean activeSession = user != null && sessionId != null
                            && userSessionRepository.existsByIdAndUserIdAndRevokedFalseAndExpiresAtAfter(
                                    sessionId, user.getId(), LocalDateTime.now());
                    if (activeSession) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        // A non-expired JWT can still fail here — diagnose why, since
                        // the request falls through unauthenticated (401) with no
                        // other signal: either the user row vanished for this
                        // email+tenant pair, or the session tied to this token's
                        // sessionId claim was revoked/expired (e.g. logged in
                        // elsewhere, or a stale token survived past its session).
                        log.warn("[AUTH_401_DIAGNOSIS] path={} userFound={} tenantId={} sessionId={} reason={}",
                                request.getRequestURI(), user != null, tenantId, sessionId,
                                user == null ? "USER_NOT_FOUND_FOR_TENANT"
                                        : sessionId == null ? "NO_SESSION_CLAIM_IN_TOKEN"
                                        : "SESSION_REVOKED_OR_EXPIRED");
                    }
                }
            } catch (ExpiredJwtException e) {
                // Token is expired — signal to frontend for refresh
                response.setHeader("Token-Expired", "true");
                log.warn("[AUTH_EXPIRED] path={} message={}", request.getRequestURI(), e.getMessage());
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

    private TokenCandidate extractToken(HttpServletRequest request) {
        // Check Authorization header first
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return new TokenCandidate(header.substring(7), false);
        }
        String cookieToken = authCookieService.readAccessToken(request);
        if (StringUtils.hasText(cookieToken)) {
            return new TokenCandidate(cookieToken, true);
        }
        // Native browser EventSource cannot set custom request headers, so the
        // SSE stream is the one endpoint that authenticates via a short-lived
        // access token passed as a query parameter instead of a header/cookie.
        if ("/api/sse/subscribe".equals(request.getRequestURI())) {
            String queryToken = request.getParameter("access_token");
            if (StringUtils.hasText(queryToken)) {
                return new TokenCandidate(queryToken, false);
            }
        }
        return new TokenCandidate(null, false);
    }

    private boolean isUnsafeMethod(String method) {
        return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));
    }

    private boolean hasCsrfGuardHeader(HttpServletRequest request) {
        return "cookie".equalsIgnoreCase(request.getHeader(AuthCookieService.COOKIE_TRANSPORT_HEADER))
                && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
    }

    private record TokenCandidate(String token, boolean fromCookie) {}
}
