package com.carrental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifies the email-verification code email renders as real branded HTML — not
 * the plain-text block ("Verify Your Email Address\n=========================\n...")
 * that Gmail used to display literally, because that plain text was previously sent
 * as the HTML MIME part. Regression coverage for that bug.
 *
 * <p>Uses a real {@link EmailTemplateRenderer} (backed by a real Thymeleaf engine
 * over the actual template files) rather than a mock — the whole point of this
 * test is to catch a broken real render, which a mocked renderer can't do.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SmtpMailService smtpMailService;

    private EmailService emailService;

    @Captor
    private ArgumentCaptor<String> htmlCaptor;

    @Captor
    private ArgumentCaptor<String> plainCaptor;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        emailService = new EmailService(smtpMailService, new EmailTemplateRenderer(engine));
    }

    @Test
    void verificationCodeEmail_sendsRealHtmlWithSeparatePlainTextFallback() {
        when(smtpMailService.sendPlatform(anyString(), anyString(), htmlCaptor.capture(), plainCaptor.capture()))
                .thenReturn(new SmtpMailService.SmtpResult(true, "ZEPTOMAIL", null, null, null, null));

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
                .thenReturn(new SmtpMailService.SmtpResult(true, "ZEPTOMAIL", null, null, null, null));

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
