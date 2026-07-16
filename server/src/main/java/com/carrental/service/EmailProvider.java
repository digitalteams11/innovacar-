package com.carrental.service;

/**
 * A way to actually deliver an {@link EmailMessage}. {@link SmtpEmailProvider}
 * (SMTP, port 587/465) and {@link HttpEmailProvider} (HTTPS API, port 443) are
 * the two implementations — selected by {@code app.email.provider}, dispatched
 * from {@link SmtpMailService}, which remains the one canonical entry point
 * every feature (test email, forgot-password, contracts, notifications) calls.
 */
public interface EmailProvider {

    SmtpMailService.SmtpResult send(EmailMessage message);

    /** True if this provider has enough configuration to attempt a send at all. */
    boolean isConfigured();

    /** Short label used in SmtpResult.providerUsed() and logs. */
    String label();
}
