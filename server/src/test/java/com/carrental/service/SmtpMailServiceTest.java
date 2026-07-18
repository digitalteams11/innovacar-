package com.carrental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Covers the single canonical send path every real email (test emails,
 * forgot-password codes, contract emails) shares — dispatch to
 * {@link HttpEmailProvider} (ZeptoMail). SMTP is never attempted.
 */
class SmtpMailServiceTest {

    private HttpEmailProvider httpEmailProvider;
    private SmtpMailService service;

    @BeforeEach
    void setUp() {
        httpEmailProvider = Mockito.mock(HttpEmailProvider.class);
        service = new SmtpMailService(httpEmailProvider);
    }

    @Test
    void isPlatformConfiguredDelegatesToHttpEmailProvider() {
        when(httpEmailProvider.isConfigured()).thenReturn(true);
        assertTrue(service.isPlatformConfigured());

        when(httpEmailProvider.isConfigured()).thenReturn(false);
        assertFalse(service.isPlatformConfigured());
    }

    @Test
    void activeProviderIsAlwaysZeptomail() {
        assertEquals("ZEPTOMAIL", service.activeProvider());
    }

    @Test
    void rejectsSendWhenNoRecipientGiven() {
        SmtpMailService.SmtpResult result = service.sendPlatform("", "Subject", "Body");

        assertFalse(result.sent());
        assertEquals("EMAIL_NO_RECIPIENT", result.errorCode());
        Mockito.verifyNoInteractions(httpEmailProvider);
    }

    @Test
    void delegatesSuccessfulSendToHttpEmailProvider() {
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                SmtpMailService.SmtpResult.success("ZeptoMail API"));

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        assertTrue(result.sent());
        Mockito.verify(httpEmailProvider).send(Mockito.any());
    }

    @Test
    void delegatesFailedSendToHttpEmailProvider() {
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                SmtpMailService.SmtpResult.failure("ZeptoMail API", "boom", "EMAIL_API_SEND_FAILED"));

        SmtpMailService.SmtpResult result = service.sendPlatform("test@example.com", "Subject", "Body");

        assertFalse(result.sent());
        assertEquals("EMAIL_API_SEND_FAILED", result.errorCode());
    }

    @Test
    void sendForTenantIgnoresTenantIdAndDelegatesToHttpEmailProvider() {
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                SmtpMailService.SmtpResult.success("ZeptoMail API"));

        SmtpMailService.SmtpResult result = service.sendForTenant(42L, "test@example.com", "Subject", "Body");

        assertTrue(result.sent());
        Mockito.verify(httpEmailProvider).send(Mockito.any());
    }

    @Test
    void htmlOnlyBodyGetsAPlainTextFallbackDerivedFromIt() {
        when(httpEmailProvider.send(Mockito.any())).thenReturn(
                SmtpMailService.SmtpResult.success("ZeptoMail API"));

        service.sendPlatform("test@example.com", "Subject", "<p>Hello <b>world</b></p>");

        Mockito.verify(httpEmailProvider).send(Mockito.argThat(email ->
                "Hello world".equals(email.plainBody())));
    }
}
