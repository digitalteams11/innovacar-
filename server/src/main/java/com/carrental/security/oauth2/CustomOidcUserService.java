package com.carrental.security.oauth2;

import com.carrental.dto.AuthResponse;
import com.carrental.exception.GoogleAuthException;
import com.carrental.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Runs Innovacar's account resolution (link/create/2FA/blocked checks — see
 * {@link GoogleOAuthService}) as part of the OIDC authentication step itself,
 * not in the success handler. This matters: an exception thrown here becomes
 * a genuine {@link OAuth2AuthenticationException}, which Spring Security
 * routes to {@link OAuth2LoginFailureHandler} automatically — exactly the
 * same error-handling path a token-signature or state-validation failure
 * takes. If this logic instead lived in the success handler (which only runs
 * after Spring already considers the login fully successful), a business
 * rejection like "account blocked" would have nowhere correct to go.
 *
 * <p>By the time {@code super.loadUser(...)} returns, Spring Security has
 * already verified the ID token's signature, issuer and audience against
 * Google's published JWKS — the claims read below are trusted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final GoogleOAuthService googleOAuthService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        try {
            AuthResponse authResponse = googleOAuthService.authenticateGoogleUser(
                    oidcUser.getSubject(),
                    oidcUser.getEmail(),
                    Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                    oidcUser.getFullName(),
                    oidcUser.getGivenName(),
                    oidcUser.getPicture()
            );
            return new CustomOidcUser(oidcUser, authResponse);
        } catch (GoogleAuthException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ex.getErrorCode(), ex.getMessage(), null), ex);
        } catch (Exception ex) {
            log.error("[OAUTH2_LOGIN_UNEXPECTED_ERROR] {}", ex.getMessage(), ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("OAUTH2_LOGIN_FAILED", "Google sign-in failed. Please try again.", null), ex);
        }
    }
}
