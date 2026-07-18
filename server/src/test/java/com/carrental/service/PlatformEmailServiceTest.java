package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.entity.Tenant;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.repository.EmailLogRepository;
import com.carrental.repository.PlatformSettingsRepository;
import com.carrental.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

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

    @InjectMocks
    private PlatformEmailService platformEmailService;

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
}
