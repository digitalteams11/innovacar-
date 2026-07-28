package com.carrental.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the production bug where every single Google OAuth2
 * login failed with authorization_request_not_found ("This sign-in link has
 * expired") regardless of whether the state cookie was actually present:
 * the ObjectMapper here only registered OAuth2ClientJackson2Module, whose
 * polymorphic-type allowlist does not cover the plain JDK collection types
 * Spring Security's own model classes are built from (e.g.
 * OAuth2AuthorizationRequest.getScopes() is a
 * java.util.Collections$UnmodifiableSet) — deserialize() silently failed on
 * every call, save-then-load never actually round-tripped, and
 * loadAuthorizationRequest() always returned null. Registering
 * SecurityJackson2Modules alongside OAuth2ClientJackson2Module is the fix;
 * this test exercises the real save -> cookie -> load round trip end to end
 * so a regression here fails loudly instead of only surfacing as a runtime
 * warning log line in production.
 */
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository(true);

    private OAuth2AuthorizationRequest sampleRequest() {
        // Mirrors exactly what Spring Security's own
        // OAuth2AuthorizationRequestRedirectFilter builds for an OIDC login:
        // an unmodifiable Set of scopes plus an unmodifiable Map of
        // additional parameters — the two JDK collection types that
        // previously failed the allowlist.
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client-id")
                .redirectUri("https://api.innovacar.app/login/oauth2/code/google")
                .scopes(java.util.Set.of("openid", "email", "profile"))
                .state("test-state-value")
                .additionalParameters(java.util.Map.of("nonce", "test-nonce-value"))
                .attributes(java.util.Map.of("registration_id", "google"))
                .build();
    }

    private static final Pattern SET_COOKIE_VALUE = Pattern.compile("oauth2_auth_request=([^;]+)");

    @Test
    void saveThenLoad_roundTripsTheFullAuthorizationRequest() {
        OAuth2AuthorizationRequest original = sampleRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);

        String setCookieHeader = saveResponse.getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        Matcher matcher = SET_COOKIE_VALUE.matcher(setCookieHeader);
        assertThat(matcher.find()).as("Set-Cookie header must contain the oauth2_auth_request value").isTrue();
        String cookieValue = matcher.group(1);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        // This is the exact assertion that was silently failing in production:
        // loaded was always null regardless of a well-formed, present cookie.
        assertThat(loaded).isNotNull();
        assertThat(loaded.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
        assertThat(loaded.getClientId()).isEqualTo(original.getClientId());
        assertThat(loaded.getState()).isEqualTo(original.getState());
        assertThat(loaded.getScopes()).isEqualTo(original.getScopes());
        assertThat(loaded.getAdditionalParameters()).isEqualTo(original.getAdditionalParameters());
        assertThat(loaded.getGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    @Test
    void loadAuthorizationRequest_withNoCookie_returnsNullWithoutThrowing() {
        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
    }

    @Test
    void loadAuthorizationRequest_withTamperedCookie_returnsNullWithoutThrowing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, "not-valid-base64-json!!!"));
        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void removeAuthorizationRequest_returnsTheRequestAndClearsTheCookie() {
        OAuth2AuthorizationRequest original = sampleRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
        String cookieValue = extractCookieValue(saveResponse);

        MockHttpServletRequest removeRequest = new MockHttpServletRequest();
        removeRequest.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(removeRequest, removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo(original.getState());
        assertThat(removeResponse.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    private String extractCookieValue(MockHttpServletResponse response) {
        Matcher matcher = SET_COOKIE_VALUE.matcher(response.getHeader("Set-Cookie"));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
