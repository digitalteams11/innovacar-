package com.carrental.security;

import com.carrental.dto.AuthResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "rentcar_access";
    public static final String REFRESH_COOKIE = "rentcar_refresh";
    public static final String COOKIE_TRANSPORT_HEADER = "X-Auth-Transport";

    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final long refreshExpirationSeconds;

    public AuthCookieService(
            @Value("${app.auth.cookies.secure:false}") boolean secure,
            @Value("${app.auth.cookies.same-site:Strict}") String sameSite,
            @Value("${app.auth.cookies.domain:}") String domain,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain;
        this.refreshExpirationSeconds = refreshExpirationMs / 1000;
        // Never logs token values — only the cookie flags themselves, so a
        // cross-site cookie failure (browser silently dropping it) can be
        // diagnosed from this line alone: cross-site prod needs secure=true,
        // sameSite=None.
        log.info("[AUTH_COOKIE_CONFIG] secure={} sameSite={} domain={}",
                secure, sameSite, domain.isBlank() ? "(host-only)" : domain);
    }

    public boolean usesCookieTransport(HttpServletRequest request) {
        return "cookie".equalsIgnoreCase(request.getHeader(COOKIE_TRANSPORT_HEADER));
    }

    public void writeAuthCookies(HttpServletResponse response, AuthResponse auth) {
        addCookie(response, ACCESS_COOKIE, auth.getAccessToken(), "/api",
                auth.getExpiresIn() == null ? 0 : auth.getExpiresIn());
        addCookie(response, REFRESH_COOKIE, auth.getRefreshToken(), "/api/auth",
                refreshExpirationSeconds);
    }

    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "", "/api", 0);
        addCookie(response, REFRESH_COOKIE, "", "/api/auth", 0);
    }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_COOKIE);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_COOKIE);
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void addCookie(HttpServletResponse response, String name, String value,
                           String path, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ofSeconds(maxAgeSeconds));
        if (domain != null && !domain.isBlank()) builder.domain(domain);
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
