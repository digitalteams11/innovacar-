package com.carrental.security;

import com.carrental.repository.UserRepository;
import com.carrental.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSessionRepository userSessionRepository = mock(UserSessionRepository.class);
    private final AuthCookieService authCookieService = mock(AuthCookieService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtTokenProvider, userRepository, userSessionRepository, authCookieService);

    @Test
    void unsafeCookieRequestWithoutCsrfGuardIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vehicles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(authCookieService.readRefreshToken(request)).thenReturn("refresh-token");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_GUARD_MISSING");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void unsafeCookieRequestWithCsrfGuardContinuesFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vehicles");
        request.addHeader(AuthCookieService.COOKIE_TRANSPORT_HEADER, "cookie");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(authCookieService.readRefreshToken(request)).thenReturn("refresh-token");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void queryStringTokenIsIgnoredForAccountAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/vehicles");
        request.setParameter("token", "jwt-in-url");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(jwtTokenProvider, never()).validateAccessToken(anyString());
        verify(chain).doFilter(request, response);
    }

    /**
     * Regression guard for the production 401 on public registration: the
     * canonical route is /api/auth/register (see AuthController's
     * @RequestMapping("/api/auth")), and this filter must never require a
     * JWT/cookie for it — the request must reach the controller even with
     * no Authorization header and no cookies at all.
     */
    @Test
    void registerRouteIsNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void loginRouteIsNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void protectedRouteIsStillFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/vehicles");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
