package com.carrental.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Maps every way the Google OAuth2 flow can fail — cancelled/denied consent,
 * a forged or expired {@code state} (see {@link CookieOAuth2AuthorizationRequestRepository}),
 * Google being unreachable, an invalid token response, or one of our own
 * business rejections thrown from {@link CustomOidcUserService} (blocked
 * account, missing email, provider mismatch, ...) — to a stable
 * {@code oauth2error} code, then redirects back to the login page so the
 * frontend can show a friendly, translated message. Never exposes the raw
 * exception message or stack trace to the browser.
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // Our own GoogleAuthException/CustomOidcUserService error codes are already
    // frontend-safe (see GoogleAuthException) — pass them through unchanged
    // rather than re-mapping them to a generic code.
    private static final Set<String> PASS_THROUGH_CODES = Set.of(
            "GOOGLE_EMAIL_MISSING", "GOOGLE_EMAIL_NOT_VERIFIED", "GOOGLE_TOKEN_INVALID",
            "ACCOUNT_BLOCKED", "ACCOUNT_SUSPENDED", "TENANT_NOT_FOUND", "PROVIDER_MISMATCH",
            "OAUTH2_LOGIN_FAILED");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String code = mapErrorCode(exception);
        log.warn("[OAUTH2_LOGIN_FAILURE] code={} message={}", code, exception.getMessage());
        String redirectTarget = frontendUrl.replaceAll("/+$", "")
                + "/#/login?oauth2error=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
        response.sendRedirect(redirectTarget);
    }

    private String mapErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception) {
            String rawCode = oAuth2Exception.getError().getErrorCode();
            if (rawCode != null && PASS_THROUGH_CODES.contains(rawCode)) {
                return rawCode;
            }
            return switch (rawCode == null ? "" : rawCode) {
                case "access_denied" -> "OAUTH2_ACCESS_DENIED";
                case "invalid_state_parameter", "invalid_state" -> "OAUTH2_INVALID_STATE";
                case "authorization_request_not_found" -> "OAUTH2_EXPIRED_AUTHORIZATION";
                case "invalid_token_response", "invalid_id_token", "invalid_nonce" -> "OAUTH2_INVALID_TOKEN";
                case "server_error", "temporarily_unavailable" -> "OAUTH2_GOOGLE_UNAVAILABLE";
                default -> "OAUTH2_LOGIN_FAILED";
            };
        }
        return "OAUTH2_LOGIN_FAILED";
    }
}
