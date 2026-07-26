package com.carrental.security.oauth2;

import com.carrental.dto.AuthResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

/**
 * Wraps Spring Security's verified {@link OidcUser} to additionally carry the
 * {@link AuthResponse} (Innovacar's own JWT pair + resolved user/role)
 * produced by {@link CustomOidcUserService#loadUser} — computed exactly once,
 * during authentication, and read back by
 * {@link com.carrental.security.oauth2.OAuth2LoginSuccessHandler} without
 * re-running any resolution logic.
 */
public class CustomOidcUser implements OidcUser {

    private final OidcUser delegate;
    private final AuthResponse authResponse;

    public CustomOidcUser(OidcUser delegate, AuthResponse authResponse) {
        this.delegate = delegate;
        this.authResponse = authResponse;
    }

    public AuthResponse getAuthResponse() {
        return authResponse;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
