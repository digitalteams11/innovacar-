package com.carrental.security.oauth2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Marks the in-flight Google OAuth2 login as desktop-originated so
 * {@link OAuth2LoginSuccessHandler}/{@link OAuth2LoginFailureHandler} can
 * redirect back to the {@code innovacar://} deep link instead of the web
 * frontend once Google's callback comes back — see GoogleAuthButton.tsx,
 * which appends {@code ?desktop=true} only when running inside Electron.
 *
 * <p>A separate plain cookie (not the OAuth2AuthorizationRequest itself) so
 * this never touches Spring Security's own state/PKCE handling in
 * {@link CookieOAuth2AuthorizationRequestRepository} — same short lifetime
 * (the whole Google round trip is a handful of seconds), same cookie
 * attributes.
 */
@Component
public class DesktopOAuthOriginFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "oauth2_origin";
    public static final String DESKTOP_VALUE = "desktop";
    private static final int COOKIE_MAX_AGE_SECONDS = 180;

    private final boolean secure;

    public DesktopOAuthOriginFilter(@Value("${app.auth.cookies.secure:false}") boolean secure) {
        this.secure = secure;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("/oauth2/authorization/google".equals(request.getRequestURI())
                && "true".equals(request.getParameter("desktop"))) {
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, DESKTOP_VALUE)
                    .httpOnly(true)
                    .secure(secure)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofSeconds(COOKIE_MAX_AGE_SECONDS))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        chain.doFilter(request, response);
    }

    static boolean isDesktopOrigin(HttpServletRequest request) {
        if (request.getCookies() == null) return false;
        return java.util.Arrays.stream(request.getCookies())
                .anyMatch(c -> COOKIE_NAME.equals(c.getName()) && DESKTOP_VALUE.equals(c.getValue()));
    }

    static void clearCookie(HttpServletResponse response, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
