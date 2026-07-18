package com.carrental.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifies the email-verification code email renders as real branded HTML — not
 * the plain-text block ("Verify Your Email Address\n=========================\n...")
 * that Gmail used to display literally, because that plain text was previously sent
 * as the HTML MIME part. Regression coverage for that bug.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SmtpMailService smtpMailService;

    @InjectMocks
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<String> htmlCaptor;

    @Captor
    private ArgumentCaptor<String> plainCaptor;

    @Test
    void verificationCodeEmail_sendsRealHtmlWithSeparatePlainTextFallback() {
        when(smtpMailService.sendPlatform(anyString(), anyString(), htmlCaptor.capture(), plainCaptor.capture()))
                .thenReturn(new SmtpMailService.SmtpResult(true, "ZEPTOMAIL", null, null, null));

        emailService.sendEmailVerificationCodeEmail("user@example.com", "Yassine", "271888", 10);

        String html = htmlCaptor.getValue();
        String plain = plainCaptor.getValue();

        // Must be an actual HTML document, not the old raw "=====" underline block.
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).doesNotContain("=====");
        assertThat(html).contains("Your verification code");
        assertThat(html).contains("271 888");
        assertThat(html).contains("10 minutes");
        assertThat(html).contains("Innovacar");

        // Plain-text fallback still exists and is human-readable, not HTML-escaped soup.
        assertThat(plain).doesNotContain("<");
        assertThat(plain).contains("271888");
        assertThat(plain).contains("10 minutes");
    }

    @Test
    void verificationLinkEmail_sendsRealHtmlWithSeparatePlainTextFallback() {
        when(smtpMailService.sendPlatform(anyString(), anyString(), htmlCaptor.capture(), plainCaptor.capture()))
                .thenReturn(new SmtpMailService.SmtpResult(true, "ZEPTOMAIL", null, null, null));

        emailService.sendVerificationEmail("user@example.com", "raw-token-123", "https://app.innovacar.app");

        String html = htmlCaptor.getValue();
        String plain = plainCaptor.getValue();

        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).doesNotContain("=====");
        assertThat(html).contains("https://app.innovacar.app/verify-email?token=raw-token-123");

        assertThat(plain).doesNotContain("<");
        assertThat(plain).contains("https://app.innovacar.app/verify-email?token=raw-token-123");
    }
}
