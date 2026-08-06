package com.carrental.controller;

import com.carrental.repository.UserRepository;
import com.carrental.repository.UserSessionRepository;
import com.carrental.security.AuthCookieService;
import com.carrental.security.JwtAuthenticationFilter;
import com.carrental.security.JwtTokenProvider;
import com.carrental.security.PasswordConfig;
import com.carrental.security.SecurityConfig;
import com.carrental.security.SubscriptionFilter;
import com.carrental.security.UserDetailsServiceImpl;
import com.carrental.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.carrental.security.oauth2.CustomOidcUserService;
import com.carrental.security.oauth2.DesktopOAuthOriginFilter;
import com.carrental.security.oauth2.OAuth2LoginFailureHandler;
import com.carrental.security.oauth2.OAuth2LoginSuccessHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the REAL {@link SecurityConfig} filter chain (including
 * {@link JwtAuthenticationFilter} and {@link SubscriptionFilter}, not stubs)
 * against {@link HealthController}, with only their repository/service
 * dependencies mocked out — so this test fails if any future change to a
 * security filter accidentally starts requiring auth for /health, which is
 * exactly the class of bug that turns into a Railway healthcheck failure
 * ("Deploy: SUCCESS, Healthcheck: FAILED") without ever showing up as an
 * application-level error.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = HealthEndpointSecurityTest.TestConfig.class)
class HealthEndpointSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void healthReturns200Anonymously() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthWorksWithoutAuthorizationHeader() throws Exception {
        // No Authorization header set at all — must not be rejected by JwtAuthenticationFilter.
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthAliasReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void apiHealthAliasReturns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void optionsHealthIsAllowed() throws Exception {
        mockMvc.perform(options("/health")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Origin", "https://innovacar.app"))
                .andExpect(status().isOk());
    }

    @Test
    void googleOAuth2AuthorizationRedirectsToGoogleAnonymously() throws Exception {
        // No Authorization header, no cookies at all — reproduces `curl -I
        // https://api.innovacar.app/oauth2/authorization/google` exactly.
        // Must be a 3xx redirect straight to Google, never this app's own
        // 401 JSON (that JSON response means the request was denied by
        // anyRequest().authenticated() instead of ever reaching Spring
        // Security's OAuth2AuthorizationRequestRedirectFilter).
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://accounts.google.com/**"));
    }

    @Test
    void googleOAuth2AuthorizationIgnoresStaleAuthorizationHeader() throws Exception {
        // A stale/garbage Authorization header (e.g. an old localStorage JWT
        // still attached by a shared HTTP client) must not turn this into a
        // 401 — JwtAuthenticationFilter.shouldNotFilter() must skip /oauth2/**
        // entirely regardless of what credentials happen to be present.
        mockMvc.perform(get("/oauth2/authorization/google")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://accounts.google.com/")));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class})
    static class TestConfig {

        @Bean
        HealthController healthController() {
            return new HealthController();
        }

        @Bean
        Environment environment() {
            return new StandardEnvironment();
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        UserSessionRepository userSessionRepository() {
            return Mockito.mock(UserSessionRepository.class);
        }

        @Bean
        AuthCookieService authCookieService() {
            return Mockito.mock(AuthCookieService.class);
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return Mockito.mock(JwtTokenProvider.class);
        }

        @Bean
        UserDetailsServiceImpl userDetailsService(UserRepository userRepository) {
            return new UserDetailsServiceImpl(userRepository);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtTokenProvider jwtTokenProvider,
                UserRepository userRepository,
                UserSessionRepository userSessionRepository,
                AuthCookieService authCookieService) {
            return new JwtAuthenticationFilter(jwtTokenProvider, userRepository, userSessionRepository, authCookieService);
        }

        @Bean
        SubscriptionFilter subscriptionFilter() {
            return new SubscriptionFilter();
        }

        @Bean
        DesktopOAuthOriginFilter desktopOAuthOriginFilter() {
            return new DesktopOAuthOriginFilter(false);
        }

        @Bean
        CookieOAuth2AuthorizationRequestRepository oAuth2AuthorizationRequestRepository() {
            return Mockito.mock(CookieOAuth2AuthorizationRequestRepository.class);
        }

        @Bean
        CustomOidcUserService customOidcUserService() {
            return Mockito.mock(CustomOidcUserService.class);
        }

        @Bean
        OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler() {
            return Mockito.mock(OAuth2LoginSuccessHandler.class);
        }

        @Bean
        OAuth2LoginFailureHandler oAuth2LoginFailureHandler() {
            return Mockito.mock(OAuth2LoginFailureHandler.class);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration google = ClientRegistration.withRegistrationId("google")
                    .clientId("test-client-id")
                    .clientSecret("test-client-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "email", "profile")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .userNameAttributeName("sub")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .clientName("Google")
                    .build();
            return new InMemoryClientRegistrationRepository(google);
        }
    }
}
