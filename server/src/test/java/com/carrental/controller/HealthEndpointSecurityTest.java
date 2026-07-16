package com.carrental.controller;

import com.carrental.repository.UserRepository;
import com.carrental.repository.UserSessionRepository;
import com.carrental.security.AuthCookieService;
import com.carrental.security.JwtAuthenticationFilter;
import com.carrental.security.JwtTokenProvider;
import com.carrental.security.SecurityConfig;
import com.carrental.security.SubscriptionFilter;
import com.carrental.security.UserDetailsServiceImpl;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
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
    }
}
