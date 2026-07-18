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

    private void configure(String apiToken, String fromEmail) {
        ReflectionTestUtils.setField(provider, "apiToken", apiToken);
        ReflectionTestUtils.setField(provider, "fromEmail", fromEmail);
    }

    @Test
    void notConfiguredWithoutApiToken() {
        configure("", "noreply@innovacar.app");
        assertFalse(provider.isConfigured());
    }

    @Test
    void notConfiguredWithoutFromEmail() {
        configure("token", "");
        assertFalse(provider.isConfigured());
    }

    @Test
    void configuredWithTokenAndFromEmail() {
        configure("token", "noreply@innovacar.app");
        assertTrue(provider.isConfigured());
    }

    @Test
    void sendWithMissingTokenReturnsConfigurationMissingWithoutAttemptingNetworkCall() {
        configure("", "noreply@innovacar.app");
        EmailMessage message = new EmailMessage("user@example.com", "Subject", "<p>Hi</p>", null, null, null, null);

        SmtpMailService.SmtpResult result = provider.send(message);

        assertFalse(result.sent());
        assertEquals("EMAIL_CONFIGURATION_MISSING", result.errorCode());
    }

    @Test
    void sendWithMissingFromEmailReturnsConfigurationMissingWithoutAttemptingNetworkCall() {
        configure("token", "");
        EmailMessage message = new EmailMessage("user@example.com", "Subject", "<p>Hi</p>", null, null, null, null);

        SmtpMailService.SmtpResult result = provider.send(message);

        assertFalse(result.sent());
        assertEquals("EMAIL_CONFIGURATION_MISSING", result.errorCode());
    }

    @Test
    void sendWithMissingRecipientIsRejectedWithoutAttemptingNetworkCall() {
        configure("token", "noreply@innovacar.app");
        EmailMessage message = new EmailMessage("", "Subject", "<p>Hi</p>", null, null, null, null);

        SmtpMailService.SmtpResult result = provider.send(message);

        assertFalse(result.sent());
        assertEquals("EMAIL_NO_RECIPIENT", result.errorCode());
    }

    @Test
    void labelNeverIncludesTheApiToken() {
        configure("super-secret-token-value", "noreply@innovacar.app");
        assertFalse(provider.label().contains("super-secret-token-value"));
    }

    @Test
    void baseUrlDefaultsToZeptomail() {
        configure("token", "noreply@innovacar.app");
        assertEquals("https://api.zeptomail.com", provider.baseUrl());
    }

    @Test
    void tokenPresentReflectsConfiguredToken() {
        configure("token", "noreply@innovacar.app");
        assertTrue(provider.tokenPresent());

        configure("", "noreply@innovacar.app");
        assertFalse(provider.tokenPresent());
    }

    @Test
    void detectsDuplicatedZohoPrefixInConfiguredToken() {
        configure("Zoho-enczapikey abc123", "noreply@innovacar.app");
        assertTrue(provider.tokenPrefixWasDuplicated());
    }

    @Test
    void doesNotFlagBareSecretAsDuplicatedPrefix() {
        configure("abc123", "noreply@innovacar.app");
        assertFalse(provider.tokenPrefixWasDuplicated());
    }
}
