package com.carrental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpEmailProviderTest {

    private HttpEmailProvider provider;

    @BeforeEach
    void setUp() {
        provider = new HttpEmailProvider();
    }

    private void configure(String activeProvider, String apiToken, String fromEmail) {
        ReflectionTestUtils.setField(provider, "provider", activeProvider);
        ReflectionTestUtils.setField(provider, "apiToken", apiToken);
        ReflectionTestUtils.setField(provider, "fromEmail", fromEmail);
    }

    @Test
    void notConfiguredWhenProviderIsSmtp() {
        configure("SMTP", "token", "noreply@innovacar.app");
        assertFalse(provider.isConfigured());
    }

    @Test
    void notConfiguredWithoutApiToken() {
        configure("ZEPTOMAIL", "", "noreply@innovacar.app");
        assertFalse(provider.isConfigured());
    }

    @Test
    void notConfiguredWithoutFromEmail() {
        configure("ZEPTOMAIL", "token", "");
        assertFalse(provider.isConfigured());
    }

    @Test
    void configuredWhenZeptomailWithTokenAndFromEmail() {
        configure("ZEPTOMAIL", "token", "noreply@innovacar.app");
        assertTrue(provider.isConfigured());
    }

    @Test
    void zohoApiReturnsNotImplementedRatherThanAttemptingOAuth() {
        configure("ZOHO_API", "token", "noreply@innovacar.app");
        EmailMessage message = new EmailMessage("user@example.com", "Subject", "<p>Hi</p>", null, null, null, null);

        SmtpMailService.SmtpResult result = provider.send(message);

        assertFalse(result.sent());
        assertEquals("EMAIL_PROVIDER_NOT_IMPLEMENTED", result.errorCode());
    }

    @Test
    void sendWithSmtpProviderSelectedReturnsMisconfiguredWithoutAttemptingNetworkCall() {
        configure("SMTP", "token", "noreply@innovacar.app");
        EmailMessage message = new EmailMessage("user@example.com", "Subject", "<p>Hi</p>", null, null, null, null);

        SmtpMailService.SmtpResult result = provider.send(message);

        assertFalse(result.sent());
        assertEquals("EMAIL_PROVIDER_MISCONFIGURED", result.errorCode());
    }

    @Test
    void sendWithMissingTokenReturnsConfigurationMissingWithoutAttemptingNetworkCall() {
        configure("ZEPTOMAIL", "", "noreply@innovacar.app");
        EmailMessage message = new EmailMessage("user@example.com", "Subject", "<p>Hi</p>", null, null, null, null);

        SmtpMailService.SmtpResult result = provider.send(message);

        assertFalse(result.sent());
        assertEquals("EMAIL_CONFIGURATION_MISSING", result.errorCode());
    }

    @Test
    void sendWithMissingRecipientIsRejectedWithoutAttemptingNetworkCall() {
        configure("ZEPTOMAIL", "token", "noreply@innovacar.app");
        EmailMessage message = new EmailMessage("", "Subject", "<p>Hi</p>", null, null, null, null);

        SmtpMailService.SmtpResult result = provider.send(message);

        assertFalse(result.sent());
        assertEquals("EMAIL_NO_RECIPIENT", result.errorCode());
    }

    @Test
    void labelNeverIncludesTheApiToken() {
        configure("ZEPTOMAIL", "super-secret-token-value", "noreply@innovacar.app");
        assertFalse(provider.label().contains("super-secret-token-value"));
    }
}
