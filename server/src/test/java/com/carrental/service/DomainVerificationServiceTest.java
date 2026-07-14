package com.carrental.service;

import com.carrental.entity.WhiteLabelSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class DomainVerificationServiceTest {

    private DomainVerificationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DomainVerificationService();
        setField(service, "baseDomain", "innovacar.app");
        setField(service, "cnameTarget", "app.innovacar.app");
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = DomainVerificationService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void generateVerificationToken_isUniqueAndPrefixed() {
        String token1 = service.generateVerificationToken();
        String token2 = service.generateVerificationToken();
        assertThat(token1).startsWith("innovacar-verify-");
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).hasSize("innovacar-verify-".length() + 32);
    }

    @Test
    void buildDnsInstructions_pointsAtConfiguredCnameTarget() {
        WhiteLabelSettings settings = WhiteLabelSettings.builder()
                .customDomain("rent.myagency.com")
                .verificationToken("innovacar-verify-abc123")
                .build();

        DomainVerificationService.DnsInstructions instructions = service.buildDnsInstructions(settings);

        assertThat(instructions.txtRecordName()).isEqualTo("_innovacar-verify.rent.myagency.com");
        assertThat(instructions.txtRecordValue()).isEqualTo("innovacar-verify-abc123");
        assertThat(instructions.cnameRecordName()).isEqualTo("rent.myagency.com");
        assertThat(instructions.cnameRecordValue()).isEqualTo("app.innovacar.app");
    }

    @Test
    void verify_noDomainConfigured_failsWithoutNetworkCall() {
        WhiteLabelSettings settings = WhiteLabelSettings.builder().build();

        DomainVerificationService.VerificationResult result = service.verify(settings);

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.error()).contains("No custom domain configured");
    }

    @Test
    void verify_noVerificationToken_failsWithoutNetworkCall() {
        WhiteLabelSettings settings = WhiteLabelSettings.builder()
                .customDomain("rent.myagency.com")
                .build();

        DomainVerificationService.VerificationResult result = service.verify(settings);

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.error()).contains("No verification token");
    }

    @Test
    void isValidSubdomainSlug_acceptsLowercaseAlphanumericHyphen() {
        assertThat(service.isValidSubdomainSlug("myagency")).isTrue();
        assertThat(service.isValidSubdomainSlug("my-agency-2")).isTrue();
        assertThat(service.isValidSubdomainSlug("MyAgency")).isFalse();
        assertThat(service.isValidSubdomainSlug("-leading-hyphen")).isFalse();
        assertThat(service.isValidSubdomainSlug("has space")).isFalse();
        assertThat(service.isValidSubdomainSlug(null)).isFalse();
    }

    @Test
    void slugify_normalizesToValidSlug() {
        assertThat(service.slugify("My Agency!!")).isEqualTo("my-agency");
        assertThat(service.slugify("  Rent_Car--Now  ")).isEqualTo("rent-car-now");
        assertThat(service.slugify(null)).isNull();
    }
}
