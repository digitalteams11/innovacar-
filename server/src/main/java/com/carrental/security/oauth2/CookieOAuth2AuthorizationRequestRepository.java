package com.carrental.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/**
 * Stores the in-flight {@link OAuth2AuthorizationRequest} (Spring Security's
 * own generated {@code state} + PKCE verifier — the actual CSRF/replay
 * protection for the OAuth2 Authorization Code flow) in a short-lived,
 * httpOnly cookie instead of an {@code HttpSession}.
 *
 * <p>This API is fully stateless ({@code SessionCreationPolicy.STATELESS},
 * see {@link com.carrental.security.SecurityConfig}) — the default
 * {@code HttpSessionOAuth2AuthorizationRequestRepository} would silently
 * create a session (or lose the request across Railway instances/redeploys
 * if it ever scaled beyond one), so this replaces it for the one part of
 * the app that briefly needs to hold state across two separate requests
 * (the redirect-to-Google, then Google's redirect back).
 *
 * <p>Serialization uses Jackson (via Spring Security's own
 * {@link OAuth2ClientJackson2Module}), deliberately NOT native Java
 * serialization — this cookie's bytes round-trip through the end user's
 * browser and are attacker-editable (httpOnly only blocks JS access, not
 * the user/an extension directly rewriting their own cookie), so avoiding
 * {@code ObjectInputStream} on that input avoids the classic Java
 * deserialization gadget-chain risk. A tampered/corrupt cookie here simply
 * fails to deserialize and is treated as "no authorization request found"
 * (see {@link #loadAuthorizationRequest}), which surfaces as a normal OAuth2
 * error, never a security issue.
 *
 * <p>{@code SameSite=Lax} (not the app's usual {@code Strict}/{@code None})
 * is required specifically for this cookie: Google's redirect back to
 * {@code /login/oauth2/code/google} is a cross-site top-level GET
 * navigation, which a {@code Strict} cookie would not be sent on at all —
 * breaking the flow — while {@code Lax} still refuses to send it on
 * cross-site subrequests/POSTs (XHR, image loads, etc.), which is all the
 * CSRF protection this specific cookie needs.
 */
@Slf4j
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_MAX_AGE_SECONDS = 180;

    private final ObjectMapper objectMapper;
    private final boolean secure;

    public CookieOAuth2AuthorizationRequestRepository(
            @Value("${app.auth.cookies.secure:false}") boolean secure) {
        this.secure = secure;
        this.objectMapper = new ObjectMapper();
        // SecurityJackson2Modules registers the polymorphic-type allowlist (via
        // activateDefaultTyping + a validator) that permits deserializing the
        // JDK collection types Spring Security's own model classes are built
        // from — e.g. OAuth2AuthorizationRequest.getScopes() is a
        // java.util.Collections$UnmodifiableSet. Without it, every single
        // deserialize() call below fails with "... is not in the allowlist"
        // (see https://github.com/spring-projects/spring-security/issues/4370),
        // loadAuthorizationRequest() always returns null regardless of whether
        // the cookie round-tripped correctly, and every OAuth2 login
        // unconditionally fails with authorization_request_not_found. Registering
        // only OAuth2ClientJackson2Module (as this used to) is not sufficient on
        // its own — that module adds mixins for the OAuth2-specific types, but
        // the base allowlist for the JDK types they're composed of comes from
        // SecurityJackson2Modules.
        this.objectMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        this.objectMapper.registerModule(new OAuth2ClientJackson2Module());
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        String serialized = serialize(authorizationRequest);
        if (serialized == null) {
            deleteCookie(response);
            return;
        }
        addCookie(response, serialized);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                    HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            deleteCookie(response);
        }
        return authorizationRequest;
    }

    private java.util.Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return java.util.Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private void addCookie(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(COOKIE_MAX_AGE_SECONDS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void deleteCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(authorizationRequest);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            log.warn("[OAUTH2_AUTH_REQUEST_SERIALIZE_FAILED] {}", e.getMessage());
            return null;
        }
    }

    private OAuth2AuthorizationRequest deserialize(String cookieValue) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(cookieValue);
            return objectMapper.readValue(new String(json, StandardCharsets.UTF_8), OAuth2AuthorizationRequest.class);
        } catch (Exception e) {
            // Tampered, expired-format, or otherwise unreadable cookie — treated as
            // "no authorization request in flight", which Spring Security surfaces
            // as a normal authorization_request_not_found OAuth2 error.
            log.warn("[OAUTH2_AUTH_REQUEST_DESERIALIZE_FAILED] {}", e.getMessage());
            return null;
        }
    }
}
