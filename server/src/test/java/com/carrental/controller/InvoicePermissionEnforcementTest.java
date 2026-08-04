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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers permission enforcement (item 4) for the three new invoice PDF/
 * export/email endpoints' {@code @PreAuthorize("@rolePermissionService.has(...)")}
 * SpEL guards. Mirrors {@link AdditionalDriverPermissionEnforcementTest}'s
 * exact mechanism: a minimal Spring MVC + method-security-only context
 * (not {@code @SpringBootTest}/{@code @WebMvcTest}) carrying fake endpoints
 * with the *same* annotation strings as {@link InvoiceController}, backed by
 * a fake "rolePermissionService" bean whose grant/deny is toggled per test —
 * so the method-security mechanism itself is exercised in isolation from
 * InvoiceController's real (heavily-mocked) dependencies.
 *
 * <p>The individual-PDF endpoint's guard is an OR of two permissions
 * ({@code INVOICE_PDF_DOWNLOAD} or {@code INVOICE_VIEW}) — both branches are
 * covered: neither permission granted -> rejected; either one alone ->
 * allowed.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = InvoicePermissionEnforcementTest.TestConfig.class)
class InvoicePermissionEnforcementTest {

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
        fakeRolePermissionService.grantedPermissions.clear();
    }

    // ── GET /{id}/pdf — INVOICE_PDF_DOWNLOAD or INVOICE_VIEW ────────────────

    @Test
    @WithMockUser
    void downloadingInvoicePdfWithNeitherPermissionIsRejected() throws Exception {
        mockMvc.perform(get("/api/invoices/1/pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void downloadingInvoicePdfWithOnlyDownloadPermissionIsAllowed() throws Exception {
        fakeRolePermissionService.grantedPermissions.add("INVOICE_PDF_DOWNLOAD");
        mockMvc.perform(get("/api/invoices/1/pdf"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void downloadingInvoicePdfWithOnlyViewPermissionIsAllowed() throws Exception {
        fakeRolePermissionService.grantedPermissions.add("INVOICE_VIEW");
        mockMvc.perform(get("/api/invoices/1/pdf"))
                .andExpect(status().isOk());
    }

    // ── POST /export/pdf — INVOICE_EXPORT ───────────────────────────────────

    @Test
    @WithMockUser
    void exportingInvoicesPdfWithoutExportPermissionIsRejected() throws Exception {
        mockMvc.perform(post("/api/invoices/export/pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void exportingInvoicesPdfWithExportPermissionIsAllowed() throws Exception {
        fakeRolePermissionService.grantedPermissions.add("INVOICE_EXPORT");
        mockMvc.perform(post("/api/invoices/export/pdf"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void viewPermissionAloneDoesNotGrantExport() throws Exception {
        fakeRolePermissionService.grantedPermissions.add("INVOICE_VIEW");
        mockMvc.perform(post("/api/invoices/export/pdf"))
                .andExpect(status().isForbidden());
    }

    // ── POST /{id}/email — INVOICE_EMAIL_SEND ───────────────────────────────

    @Test
    @WithMockUser
    void emailingInvoiceWithoutEmailSendPermissionIsRejected() throws Exception {
        mockMvc.perform(post("/api/invoices/1/email"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void emailingInvoiceWithEmailSendPermissionIsAllowed() throws Exception {
        fakeRolePermissionService.grantedPermissions.add("INVOICE_EMAIL_SEND");
        mockMvc.perform(post("/api/invoices/1/email"))
                .andExpect(status().isOk());
    }

    // ── Unauthenticated ──────────────────────────────────────────────────────

    @Test
    void unauthenticatedCallToDownloadPdfIsRejected() throws Exception {
        mockMvc.perform(get("/api/invoices/1/pdf"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unauthenticatedCallToExportPdfIsRejected() throws Exception {
        mockMvc.perform(post("/api/invoices/export/pdf"))
                .andExpect(status().is4xxClientError());
    }

    /** Stand-in for the real {@code RolePermissionService} — same bean name, same {@code has(String)} shape. */
    static class FakeRolePermissionService {
        final java.util.Set<String> grantedPermissions = new java.util.HashSet<>();

        public boolean has(String permissionCode) {
            return grantedPermissions.contains(permissionCode);
        }
    }

    @RestController
    @RequestMapping("/api/invoices")
    static class FakeInvoiceController {

        @GetMapping("/{id}/pdf")
        @PreAuthorize("@rolePermissionService.has('INVOICE_PDF_DOWNLOAD') or @rolePermissionService.has('INVOICE_VIEW')")
        String downloadPdf(@PathVariable Long id) {
            return "ok";
        }

        @PostMapping("/export/pdf")
        @PreAuthorize("@rolePermissionService.has('INVOICE_EXPORT')")
        String exportPdf() {
            return "ok";
        }

        @PostMapping("/{id}/email")
        @PreAuthorize("@rolePermissionService.has('INVOICE_EMAIL_SEND')")
        String emailPdf(@PathVariable Long id) {
            return "ok";
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        FakeInvoiceController fakeInvoiceController() {
            return new FakeInvoiceController();
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
