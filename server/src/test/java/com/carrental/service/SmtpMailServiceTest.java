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
    private HttpEmailProvider httpEmailProvider;
    private SmtpMailService service;

    @BeforeEach
    void setUp() {
        platformSettingsRepository = Mockito.mock(PlatformSettingsRepository.class);
        tenantSettingsRepository = Mockito.mock(TenantSettingsRepository.class);
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "encryptionSecret", "unit-test-secret-32-bytes-long!!");
        encryptionUtil.init();
        httpEmailProvider = Mockito.mock(HttpEmailProvider.class);
        service = new SmtpMailService(platformSettingsRepository, tenantSettingsRepository, encryptionUtil, httpEmailProvider);
        // @Value fields aren't populated outside a Spring context — default to the
        // same behavior as production's default (SMTP authoritative, no fallback).
        ReflectionTestUtils.setField(service, "activeProvider", "SMTP");
        ReflectionTestUtils.setField(service, "allowProviderFallback", false);
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

    // ── Provider dispatch ────────────────────────────────────────────────────

    @Test
    void whenHttpProviderSelectedSmtpIsNeverAttempted() {
        ReflectionTestUtils.setField(service, "activeProvider", "ZEPTOMAIL");
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                new SmtpMailService.SmtpResult(true, "email API (ZEPTOMAIL)", null, null, null));

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        assertTrue(result.sent());
        Mockito.verify(httpEmailProvider).send(Mockito.any());
        // The SMTP path resolves platform config from the repository — confirm it was
        // never even consulted, i.e. SMTP was not attempted at all.
        Mockito.verifyNoInteractions(platformSettingsRepository);
    }

    @Test
    void httpProviderFailureIsNotFollowedBySmtpFallbackByDefault() {
        ReflectionTestUtils.setField(service, "activeProvider", "ZEPTOMAIL");
        ReflectionTestUtils.setField(service, "allowProviderFallback", false);
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                SmtpMailService.SmtpResult.failure("email API (ZEPTOMAIL)", "boom", "EMAIL_API_SEND_FAILED"));

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        assertFalse(result.sent());
        assertEquals("EMAIL_API_SEND_FAILED", result.errorCode());
        Mockito.verifyNoInteractions(platformSettingsRepository);
    }

    @Test
    void httpProviderFailureFallsBackToSmtpOnlyWhenExplicitlyEnabled() {
        ReflectionTestUtils.setField(service, "activeProvider", "ZEPTOMAIL");
        ReflectionTestUtils.setField(service, "allowProviderFallback", true);
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                SmtpMailService.SmtpResult.failure("email API (ZEPTOMAIL)", "boom", "EMAIL_API_SEND_FAILED"));
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        // Falls through to SMTP, which is unconfigured in this test — the point is that
        // it was actually consulted (fallback engaged), not that it succeeded.
        assertFalse(result.sent());
        Mockito.verify(platformSettingsRepository).findTopByOrderByIdAsc();
    }

    @Test
    void smtpFailureDoesNotFallBackToHttpProviderByDefault() {
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());

        service.sendPlatform("test@example.com", "Subject", "Body");

        Mockito.verifyNoInteractions(httpEmailProvider);
    }

    @Test
    void smtpFailureFallsBackToHttpProviderOnlyWhenExplicitlyEnabledAndConfigured() {
        ReflectionTestUtils.setField(service, "allowProviderFallback", true);
        when(platformSettingsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());
        when(httpEmailProvider.isConfigured()).thenReturn(true);
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                new SmtpMailService.SmtpResult(true, "email API (ZEPTOMAIL)", null, null, null));

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        assertTrue(result.sent());
        Mockito.verify(httpEmailProvider).send(Mockito.any());
    }
}
