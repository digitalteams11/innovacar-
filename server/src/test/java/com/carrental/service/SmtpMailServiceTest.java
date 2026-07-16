package com.carrental.service;

import com.carrental.entity.PlatformSettings;
import com.carrental.repository.PlatformSettingsRepository;
import com.carrental.repository.TenantSettingsRepository;
import com.carrental.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Covers the platform SMTP config resolution used by every real email send (test emails,
 * forgot-password codes, contract emails) — the single canonical path all of them share.
 */
class SmtpMailServiceTest {

    private PlatformSettingsRepository platformSettingsRepository;
    private TenantSettingsRepository tenantSettingsRepository;
    private EncryptionUtil encryptionUtil;
    private SmtpMailService service;

    @BeforeEach
    void setUp() {
        platformSettingsRepository = Mockito.mock(PlatformSettingsRepository.class);
        tenantSettingsRepository = Mockito.mock(TenantSettingsRepository.class);
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "encryptionSecret", "unit-test-secret-32-bytes-long!!");
        encryptionUtil.init();
        service = new SmtpMailService(platformSettingsRepository, tenantSettingsRepository, encryptionUtil);
    }

    private PlatformSettings validPlatformSettings() {
        return PlatformSettings.builder()
                .id(1L)
                .smtpHost("smtp.zoho.com")
                .smtpPort(587)
                .smtpUsername("contact@innovacar.app")
                .smtpPasswordEncrypted(encryptionUtil.encrypt("app-password"))
                .smtpUseTls(true)
                .smtpEnabled(true)
                .fromEmail("contact@innovacar.app")
                .fromName("Innovax Technologies")
                .build();
    }

    @Test
    void notConfiguredWhenNoRowExists() {
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());

        assertFalse(service.isPlatformConfigured());
    }

    @Test
    void notConfiguredWhenPasswordMissing() {
        PlatformSettings ps = validPlatformSettings();
        ps.setSmtpPasswordEncrypted(null);
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(ps));

        assertFalse(service.isPlatformConfigured());
    }

    @Test
    void notConfiguredWhenExplicitlyDisabled() {
        PlatformSettings ps = validPlatformSettings();
        ps.setSmtpEnabled(false);
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(ps));

        assertFalse(service.isPlatformConfigured());
    }

    @Test
    void enabledNullIsTreatedAsEnabledForBackwardCompatibility() {
        PlatformSettings ps = validPlatformSettings();
        ps.setSmtpEnabled(null);
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(ps));

        assertTrue(service.isPlatformConfigured());
    }

    @Test
    void notConfiguredWhenStoredPasswordCannotBeDecrypted() {
        PlatformSettings ps = validPlatformSettings();
        // Simulates APP_ENCRYPTION_SECRET having changed since the password was stored.
        ps.setSmtpPasswordEncrypted("not-valid-base64-ciphertext");
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(ps));

        assertFalse(service.isPlatformConfigured());
    }

    @Test
    void fullyConfiguredRowIsUsable() {
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(validPlatformSettings()));

        assertTrue(service.isPlatformConfigured());
    }

    @Test
    void rejectsSendWhenNoRecipientGiven() {
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(validPlatformSettings()));

        SmtpMailService.SmtpResult result = service.sendPlatform("", "Subject", "Body");

        assertFalse(result.sent());
        assertEquals("EMAIL_NO_RECIPIENT", result.errorCode());
    }

    @Test
    void rejectsZohoSendWhenFromEmailDoesNotMatchAuthenticatedUsername() {
        PlatformSettings ps = validPlatformSettings();
        ps.setFromEmail("someone-else@innovacar.app");
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(ps));

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        assertFalse(result.sent());
        assertEquals("SMTP_FROM_EMAIL_INVALID", result.errorCode());
    }

    @Test
    void unconfiguredSendReturnsSmtpNotConfigured() {
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        assertFalse(result.sent());
        assertEquals("SMTP_NOT_CONFIGURED", result.errorCode());
    }
}
