package com.carrental.controller;

import com.carrental.entity.Tenant;
import com.carrental.entity.WhiteLabelSettings;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.WhiteLabelSettingsRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.DomainVerificationService;
import com.carrental.service.FeatureAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhiteLabelControllerTest {

    @Mock private WhiteLabelSettingsRepository whiteLabelRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private FeatureAccessService featureAccessService;

    private DomainVerificationService domainVerificationService;
    private WhiteLabelController controller;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() throws Exception {
        domainVerificationService = new DomainVerificationService();
        var baseDomainField = DomainVerificationService.class.getDeclaredField("baseDomain");
        baseDomainField.setAccessible(true);
        baseDomainField.set(domainVerificationService, "innovacar.app");
        var cnameField = DomainVerificationService.class.getDeclaredField("cnameTarget");
        cnameField.setAccessible(true);
        cnameField.set(domainVerificationService, "app.innovacar.app");

        controller = new WhiteLabelController(whiteLabelRepository, tenantRepository, featureAccessService, domainVerificationService);
        TenantContext.setCurrentTenantId(TENANT_ID);

        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        lenient().when(featureAccessService.isEnabledForCurrentTenant("WHITE_LABEL")).thenReturn(true);
        lenient().when(whiteLabelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void saveSettings_newCustomDomain_generatesTokenAndSetsPending() {
        when(whiteLabelRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(whiteLabelRepository.findByCustomDomain("rent.myagency.com")).thenReturn(Optional.empty());

        WhiteLabelSettings body = new WhiteLabelSettings();
        body.setPrimaryColor("#111111");
        body.setAccentColor("#222222");
        body.setCustomDomain("rent.myagency.com");

        ResponseEntity<Map<String, Object>> response = controller.saveSettings(body);

        Map<String, Object> data = response.getBody();
        assertThat(data.get("domainStatus")).isEqualTo("PENDING");
        assertThat(data.get("customDomain")).isEqualTo("rent.myagency.com");
        assertThat(data.get("dnsInstructions")).isNotNull();
    }

    @Test
    void saveSettings_subdomain_isDnsVerifiedImmediatelyNeverActive() {
        when(whiteLabelRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(whiteLabelRepository.findBySubdomain("myagency")).thenReturn(Optional.empty());

        WhiteLabelSettings body = new WhiteLabelSettings();
        body.setPrimaryColor("#111111");
        body.setAccentColor("#222222");
        body.setSubdomain("MyAgency");

        ResponseEntity<Map<String, Object>> response = controller.saveSettings(body);

        Map<String, Object> data = response.getBody();
        assertThat(data.get("subdomain")).isEqualTo("myagency");
        assertThat(data.get("domainStatus")).isEqualTo("DNS_VERIFIED");
        assertThat(data.get("subdomainFull")).isEqualTo("myagency.innovacar.app");
    }

    @Test
    void saveSettings_bothDomainAndSubdomain_rejected() {
        when(whiteLabelRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        WhiteLabelSettings body = new WhiteLabelSettings();
        body.setCustomDomain("rent.myagency.com");
        body.setSubdomain("myagency");

        assertThatThrownBy(() -> controller.saveSettings(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either a custom domain or an Innovacar subdomain");
    }

    @Test
    void saveSettings_domainTakenByAnotherTenant_rejected() {
        when(whiteLabelRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        Tenant otherTenant = new Tenant();
        otherTenant.setId(2L);
        WhiteLabelSettings existing = WhiteLabelSettings.builder().tenant(otherTenant).customDomain("rent.myagency.com").build();
        when(whiteLabelRepository.findByCustomDomain("rent.myagency.com")).thenReturn(Optional.of(existing));

        WhiteLabelSettings body = new WhiteLabelSettings();
        body.setCustomDomain("rent.myagency.com");

        assertThatThrownBy(() -> controller.saveSettings(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void saveSettings_invalidLogoType_rejected() {
        when(whiteLabelRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        WhiteLabelSettings body = new WhiteLabelSettings();
        body.setLogoUrl("data:image/gif;base64,AAAA");

        assertThatThrownBy(() -> controller.saveSettings(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG, JPEG or SVG");
    }

    @Test
    void verifyDomain_noCustomDomainConfigured_throws() {
        WhiteLabelSettings existing = WhiteLabelSettings.builder().build();
        when(whiteLabelRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> controller.verifyDomain())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nothing to verify");
    }

    @Test
    void verifyDomain_unreachableDomain_reportsRealFailureNotFakeSuccess() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        WhiteLabelSettings existing = WhiteLabelSettings.builder()
                .tenant(tenant)
                .customDomain("definitely-not-a-real-domain-xyz-123.invalid")
                .verificationToken("innovacar-verify-abc")
                .domainStatus("PENDING")
                .build();
        when(whiteLabelRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existing));

        ResponseEntity<Map<String, Object>> response = controller.verifyDomain();

        Map<String, Object> data = response.getBody();
        assertThat(data.get("domainStatus")).isEqualTo("FAILED");
        assertThat(data.get("lastCheckError")).isNotNull();
    }
}
