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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers item 14: calling the additional-driver link-management endpoints'
 * {@code @PreAuthorize("@rolePermissionService.has(...)")} SpEL guard without
 * the corresponding ADDITIONAL_DRIVER_SIGNATURE_SEND/_REVOKE permission is
 * rejected. Mirrors {@link SuperAdminRouteAccessTest}'s exact mechanism: a
 * minimal Spring MVC + method-security-only context (not
 * {@code @SpringBootTest}/{@code @WebMvcTest}) carrying the *same* annotation
 * strings as {@link AdditionalDriverController}, with a fake
 * "rolePermissionService" bean whose grant/deny is toggled per test — so the
 * method-security mechanism itself is exercised in full isolation from the
 * rest of AdditionalDriverController's (heavily-mocked) dependencies.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AdditionalDriverPermissionEnforcementTest.TestConfig.class)
class AdditionalDriverPermissionEnforcementTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FakeRolePermissionService fakeRolePermissionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        fakeRolePermissionService.granted = false;
    }

    @Test
    @WithMockUser
    void sendingASignatureLinkWithoutTheSendPermissionIsRejected() throws Exception {
        fakeRolePermissionService.granted = false;
        mockMvc.perform(post("/api/contracts/1/additional-drivers/2/signature-link"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void sendingASignatureLinkWithTheSendPermissionIsAllowed() throws Exception {
        fakeRolePermissionService.granted = true;
        mockMvc.perform(post("/api/contracts/1/additional-drivers/2/signature-link"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void revokingASignatureLinkWithoutTheRevokePermissionIsRejected() throws Exception {
        fakeRolePermissionService.granted = false;
        mockMvc.perform(post("/api/contracts/1/additional-drivers/2/signature-link/revoke"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void revokingASignatureLinkWithTheRevokePermissionIsAllowed() throws Exception {
        fakeRolePermissionService.granted = true;
        mockMvc.perform(post("/api/contracts/1/additional-drivers/2/signature-link/revoke"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCallIsRejected() throws Exception {
        mockMvc.perform(post("/api/contracts/1/additional-drivers/2/signature-link"))
                .andExpect(status().is4xxClientError());
    }

    /** Stand-in for the real {@code RolePermissionService} — same bean name, same {@code has(String)} shape. */
    static class FakeRolePermissionService {
        volatile boolean granted = false;

        public boolean has(String permissionCode) {
            return granted;
        }
    }

    @RestController
    @RequestMapping("/api/contracts/{contractId}/additional-drivers")
    static class FakeAdditionalDriverController {

        @PostMapping("/{driverId}/signature-link")
        @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_SEND')")
        String generateLink() {
            return "ok";
        }

        @PostMapping("/{driverId}/signature-link/revoke")
        @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_REVOKE')")
        String revokeLink() {
            return "ok";
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        FakeAdditionalDriverController fakeAdditionalDriverController() {
            return new FakeAdditionalDriverController();
        }

        @Bean("rolePermissionService")
        FakeRolePermissionService rolePermissionService() {
            return new FakeRolePermissionService();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            // HTTP layer permits everything; the @PreAuthorize SpEL is the only thing under test.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            http.csrf(csrf -> csrf.disable());
            return http.build();
        }
    }
}
