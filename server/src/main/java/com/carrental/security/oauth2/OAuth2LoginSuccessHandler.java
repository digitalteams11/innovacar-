package com.carrental.security.oauth2;

import com.carrental.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * Runs once Spring Security has fully authenticated the Google OIDC login —
 * {@link CustomOidcUserService} already resolved/linked/created the
 * Innovacar account and generated the real JWT pair (carried on the
 * principal as {@link CustomOidcUser#getAuthResponse()}). This handler's
 * only job is handing that off to the SPA: store it under a single-use code
 * ({@link OAuth2ExchangeCodeStore}) and redirect the browser back to the
 * frontend's login page, which exchanges the code for the real tokens (see
 * {@code AuthController#exchangeOAuth2Code}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2ExchangeCodeStore exchangeCodeStore;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        if (response.isCommitted()) return;

        if (!(authentication.getPrincipal() instanceof CustomOidcUser principal)) {
            // Should be unreachable — CustomOidcUserService always returns a
            // CustomOidcUser — but fail safe rather than NPE if the wiring
            // ever changes.
            log.error("[OAUTH2_LOGIN_SUCCESS] unexpected principal type: {}",
                    authentication.getPrincipal().getClass());
            response.sendRedirect(loginUrlWithError("OAUTH2_LOGIN_FAILED"));
            return;
        }

        AuthResponse authResponse = principal.getAuthResponse();
        String code = exchangeCodeStore.store(authResponse);
        String redirectTarget = frontendUrl.replaceAll("/+$", "")
                + "/#/login?oauth2code=" + urlEncode(code);
        log.info("[OAUTH2_LOGIN_SUCCESS] userId={} twoFactorRequired={}",
                authResponse.getUserId(), Boolean.TRUE.equals(authResponse.getTwoFactorRequired()));
        response.sendRedirect(redirectTarget);
    }

    private String loginUrlWithError(String code) {
        return frontendUrl.replaceAll("/+$", "") + "/#/login?oauth2error=" + urlEncode(code);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
