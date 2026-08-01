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

    @Value("${app.auth.cookies.secure:false}")
    private boolean cookiesSecure;

    /** Registered as this exact scheme in the Windows/NSIS installer — see
     * frontend_desktop's electron-builder "protocols" config and main.cjs's
     * app.setAsDefaultProtocolClient. Never registered as Google's own OAuth
     * redirect URI (see class Javadoc on DesktopOAuthOriginFilter) — only
     * this final backend-to-desktop handoff uses it. */
    private static final String DESKTOP_CALLBACK_URL = "innovacar://auth/callback";

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
        boolean desktop = DesktopOAuthOriginFilter.isDesktopOrigin(request);

        String redirectTarget;
        if (desktop) {
            DesktopOAuthOriginFilter.clearCookie(response, cookiesSecure);
            redirectTarget = DESKTOP_CALLBACK_URL + "?code=" + urlEncode(code);
        } else {
            String base = FrontendUrls.canonicalize(frontendUrl);
            redirectTarget = base + "/#/login?oauth2code=" + urlEncode(code);
        }
        log.info("[OAUTH2_LOGIN_SUCCESS] userId={} tenantId={} twoFactorRequired={} desktop={}",
                authResponse.getUserId(), authResponse.getTenantId(),
                Boolean.TRUE.equals(authResponse.getTwoFactorRequired()), desktop);
        response.sendRedirect(redirectTarget);
    }

    private String loginUrlWithError(String code) {
        return FrontendUrls.canonicalize(frontendUrl) + "/#/login?oauth2error=" + urlEncode(code);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
