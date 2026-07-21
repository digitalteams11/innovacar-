package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.entity.Tenant;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.repository.EmailLogRepository;
import com.carrental.repository.PlatformSettingsRepository;
import com.carrental.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies the signed-contract-PDF email link: a real HTTPS button when a
 * valid token/URL exists, and a plain non-clickable fallback message
 * otherwise — never a placeholder string written into an href (the root
 * cause of the "http://(PDF link not available...)" broken Gmail link).
 */
@ExtendWith(MockitoExtension.class)
class PlatformEmailServiceTest {

    @Mock private SmtpMailService smtpMailService;
    @Mock private ContractRepository contractRepository;
    @Mock private EmailLogRepository emailLogRepository;
    @Mock private PlatformSettingsRepository platformSettingsRepository;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private PdfService pdfService;
    @Mock private DepositRepository depositRepository;
    @Mock private SupportTicketRepository supportTicketRepository;
    @Mock private com.carrental.repository.ContactRequestRepository contactRequestRepository;
    @Mock private com.carrental.repository.TenantSettingsRepository tenantSettingsRepository;
    @Mock private com.carrental.repository.ClientRepository clientRepository;
    @Mock private Environment environment;

    @InjectMocks
    private PlatformEmailService platformEmailService;

    @BeforeEach
    void stubLocalDevEnvironment() {
        // Every isValidPublicUrl call checks isDeployedEnvironment(), which reads this —
        // default to "not deployed" (no active profile) so http+localhost is allowed,
        // matching how these tests actually run (no RAILWAY_* env vars, no prod profile).
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[0]);
    }

    private Contract contractWithToken(String qrToken) {
        Tenant tenant = Tenant.builder().id(1L).name("Agency").email("agency@test.com").build();
        return Contract.builder()
                .id(42L)
                .contractNumber("CTR-2026-00001")
                .qrToken(qrToken)
                .tenant(tenant)
                .build();
    }

    @Test
    void isValidPublicUrl_acceptsProductionHttpsUrl() {
        ReflectionTestUtils.setField(platformEmailService, "publicApiUrl", "https://api.innovacar.app");
        assertThat(platformEmailService.isValidPublicUrl("https://api.innovacar.app/api/public/contracts/1/tok/pdf")).isTrue();
    }

    @Test
    void isValidPublicUrl_rejectsBlankNullAndPlaceholderText() {
        assertThat(platformEmailService.isValidPublicUrl(null)).isFalse();
        assertThat(platformEmailService.isValidPublicUrl("")).isFalse();
        assertThat(platformEmailService.isValidPublicUrl("   ")).isFalse();
        assertThat(platformEmailService.isValidPublicUrl("(PDF link not available — contact your agency)")).isFalse();
        assertThat(platformEmailService.isValidPublicUrl("http://(PDF link not available — contact your agency)")).isFalse();
    }

    @Test
    void isValidPublicUrl_rejectsLocalhostAnd192InProduction() {
        assertThat(platformEmailService.isValidPublicUrl("https://localhost/api/public/contracts/1/tok/pdf")).isFalse();
        assertThat(platformEmailService.isValidPublicUrl("https://192.168.1.5/api/public/contracts/1/tok/pdf")).isFalse();
    }

    @Test
    void isValidPublicUrl_rejectsRailwayInternalDomain() {
        assertThat(platformEmailService.isValidPublicUrl("https://backend.railway.internal/api/public/contracts/1/tok/pdf")).isFalse();
    }

    @Test
    void isValidPublicUrl_acceptsHttpOnlyForLocalDev() {
        assertThat(platformEmailService.isValidPublicUrl("http://localhost:8082/api/public/contracts/1/tok/pdf")).isTrue();
        assertThat(platformEmailService.isValidPublicUrl("http://api.innovacar.app/api/public/contracts/1/tok/pdf")).isFalse();
    }

    /**
     * Regression test for the actual production incident: PUBLIC_API_URL was
     * never set on Railway, app.public-api-url silently fell back to
     * http://localhost:8082, and isValidPublicUrl called that "valid" —
     * rendering a real, clickable localhost button in a production email.
     */
    @Test
    void isValidPublicUrl_rejectsHttpLocalhost_whenDeployedToProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        assertThat(platformEmailService.isValidPublicUrl("http://localhost:8082/api/public/contracts/1/tok/pdf")).isFalse();
    }

    @Test
    void buildPdfSection_omitsButtonWhenLocalhostLeaksThroughInProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(platformEmailService, "publicApiUrl", "http://localhost:8082");

        String html = platformEmailService.buildPdfSection(contractWithToken("secure-token-abc"));

        assertThat(html).doesNotContain("localhost");
        assertThat(html).doesNotContain("href=");
    }

    @Test
    void buildPdfDownloadUrl_isNullWithoutToken() {
        assertThat(platformEmailService.buildPdfDownloadUrl(contractWithToken(null))).isNull();
    }

    @Test
    void buildPdfDownloadUrl_usesConfiguredPublicApiUrlAndNormalizesSlashes() {
        ReflectionTestUtils.setField(platformEmailService, "publicApiUrl", "https://api.innovacar.app/");
        String url = platformEmailService.buildPdfDownloadUrl(contractWithToken("secure-token-abc"));
        assertThat(url).isEqualTo("https://api.innovacar.app/api/public/contracts/42/secure-token-abc/pdf");
        assertThat(url).doesNotContain("app//api");
    }

    @Test
    void buildPdfSection_rendersRealButtonWhenTokenExists() {
        ReflectionTestUtils.setField(platformEmailService, "publicApiUrl", "https://api.innovacar.app");
        String html = platformEmailService.buildPdfSection(contractWithToken("secure-token-abc"));
        assertThat(html).contains("href=\"https://api.innovacar.app/api/public/contracts/42/secure-token-abc/pdf\"");
        assertThat(html).contains("Download Contract PDF");
    }

    @Test
    void buildPdfSection_omitsButtonWhenTokenMissing() {
        String html = platformEmailService.buildPdfSection(contractWithToken(null));
        assertThat(html).doesNotContain("<a href");
        assertThat(html).contains("still being generated");
    }

    @Test
    void buildPdfSection_neverEmbedsPlaceholderTextInsideHref() {
        // Even if publicApiUrl were misconfigured to something invalid, the
        // section must never contain an href built from non-URL text.
        ReflectionTestUtils.setField(platformEmailService, "publicApiUrl", "not a url");
        String html = platformEmailService.buildPdfSection(contractWithToken("secure-token-abc"));
        assertThat(html).doesNotContain("<a href");
        assertThat(html).doesNotContain("http://not a url");
    }

    @Test
    void buildPdfPlainSection_showsUrlOrFallbackSentence() {
        ReflectionTestUtils.setField(platformEmailService, "publicApiUrl", "https://api.innovacar.app");
        assertThat(platformEmailService.buildPdfPlainSection(contractWithToken("secure-token-abc")))
                .contains("https://api.innovacar.app/api/public/contracts/42/secure-token-abc/pdf");
        assertThat(platformEmailService.buildPdfPlainSection(contractWithToken(null)))
                .isEqualTo("The signed PDF is still being generated. Please contact your rental agency.");
    }

    @Test
    void resolveTenantEmailLanguage_nullTenant_defaultsToEn() {
        String lang = ReflectionTestUtils.invokeMethod(platformEmailService, "resolveTenantEmailLanguage", (Object) null);
        assertThat(lang).isEqualTo("EN");
    }

    @Test
    void resolveTenantEmailLanguage_noTenantSettingsRow_defaultsToEn() {
        Tenant tenant = Tenant.builder().id(5L).name("Agency").email("agency@test.com").build();
        org.mockito.Mockito.when(tenantSettingsRepository.findByTenantId(5L)).thenReturn(java.util.Optional.empty());

        String lang = ReflectionTestUtils.invokeMethod(platformEmailService, "resolveTenantEmailLanguage", tenant);

        assertThat(lang).isEqualTo("EN");
    }

    @Test
    void resolveTenantEmailLanguage_usesTenantDefaultLanguage_uppercased() {
        Tenant tenant = Tenant.builder().id(6L).name("Agency FR").email("agency-fr@test.com").build();
        var settings = com.carrental.entity.TenantSettings.builder().language("fr").build();
        org.mockito.Mockito.when(tenantSettingsRepository.findByTenantId(6L)).thenReturn(java.util.Optional.of(settings));

        String lang = ReflectionTestUtils.invokeMethod(platformEmailService, "resolveTenantEmailLanguage", tenant);

        assertThat(lang).isEqualTo("FR");
    }

    // ── "Add missing client email" recovery flow: send must use the client's
    // ── current live email when the contract's own snapshot is blank, and
    // ── must never touch the snapshot itself (see resolveEffectiveClientEmail). ──

    @Test
    void resendContractEmail_fallsBackToLiveClientEmail_whenSnapshotBlank() {
        Tenant tenant = Tenant.builder().id(1L).name("Agency").email("agency@test.com").build();
        com.carrental.entity.Client client = com.carrental.entity.Client.builder().id(9L).email("live@example.com").build();
        Contract contract = Contract.builder()
                .id(42L).contractNumber("CTR-2026-00001").tenant(tenant).client(client)
                .clientEmail(null)
                .build();
        org.mockito.Mockito.when(contractRepository.findById(42L)).thenReturn(java.util.Optional.of(contract));
        org.mockito.Mockito.when(clientRepository.findById(9L)).thenReturn(java.util.Optional.of(client));
        org.mockito.Mockito.when(smtpMailService.sendForTenant(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(SmtpMailService.SmtpResult.success("ZEPTOMAIL"));

        SmtpMailService.SmtpResult result = platformEmailService.resendContractEmail(42L);

        assertThat(result.sent()).isTrue();
        org.mockito.Mockito.verify(smtpMailService).sendForTenant(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("live@example.com"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        // Never writes back to the contract's own snapshot field.
        assertThat(contract.getClientEmail()).isNull();
    }

    @Test
    void resendContractEmail_usesSnapshotEmail_whenPresent_neverQueriesLiveClient() {
        Tenant tenant = Tenant.builder().id(1L).name("Agency").email("agency@test.com").build();
        com.carrental.entity.Client client = com.carrental.entity.Client.builder().id(9L).email("live@example.com").build();
        Contract contract = Contract.builder()
                .id(43L).contractNumber("CTR-2026-00002").tenant(tenant).client(client)
                .clientEmail("snapshot@example.com")
                .build();
        org.mockito.Mockito.when(contractRepository.findById(43L)).thenReturn(java.util.Optional.of(contract));
        org.mockito.Mockito.when(smtpMailService.sendForTenant(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(SmtpMailService.SmtpResult.success("ZEPTOMAIL"));

        platformEmailService.resendContractEmail(43L);

        org.mockito.Mockito.verify(smtpMailService).sendForTenant(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("snapshot@example.com"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(clientRepository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resendContractEmail_noEmailAnywhere_failsWithoutQueryingClientRepository() {
        Tenant tenant = Tenant.builder().id(1L).name("Agency").email("agency@test.com").build();
        Contract contract = Contract.builder()
                .id(44L).contractNumber("CTR-2026-00003").tenant(tenant).client(null)
                .clientEmail(null)
                .build();
        org.mockito.Mockito.when(contractRepository.findById(44L)).thenReturn(java.util.Optional.of(contract));

        SmtpMailService.SmtpResult result = platformEmailService.resendContractEmail(44L);

        assertThat(result.sent()).isFalse();
        assertThat(result.errorCode()).isEqualTo("EMAIL_TO_ADDRESS_MISSING");
        org.mockito.Mockito.verifyNoInteractions(clientRepository);
    }
}
