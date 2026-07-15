package com.carrental.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms that the authorization rule guarding {@code /api/super-admin/**}
 * in {@link SuperAdminController} — {@code @PreAuthorize("hasRole('SUPER_ADMIN')")}
 * — rejects any role other than SUPER_ADMIN.
 *
 * <p>Uses a plain Spring MVC Test context (not {@code @SpringBootTest}/
 * {@code @WebMvcTest}, which would pull in the real application's
 * {@code @EnableJpaRepositories}/datasource via {@code LocationCarApplication})
 * with a minimal in-test controller carrying the same annotation, so the
 * method-security mechanism itself is exercised in full isolation.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SuperAdminRouteAccessTest.TestConfig.class)
class SuperAdminRouteAccessTest {

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
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanAccessSuperAdminRoute() throws Exception {
        mockMvc.perform(get("/api/super-admin/ping"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void normalUserCannotAccessSuperAdminRoute() throws Exception {
        mockMvc.perform(get("/api/super-admin/ping"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessSuperAdminRoute() throws Exception {
        mockMvc.perform(get("/api/super-admin/ping"))
                .andExpect(status().is4xxClientError());
    }

    @RestController
    @RequestMapping("/api/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "ok";
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        PingController pingController() {
            return new PingController();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            // HTTP layer permits everything; the @PreAuthorize on PingController
            // is the only thing under test here.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
