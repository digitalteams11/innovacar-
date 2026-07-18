package com.carrental.service;

/**
 * A way to actually deliver an {@link EmailMessage}. {@link HttpEmailProvider}
 * (ZeptoMail's HTTPS API, port 443) is the sole implementation — SMTP is never
 * used. Dispatched from {@link SmtpMailService}, which remains the one
 * canonical entry point every feature (test email, forgot-password,
 * contracts, notifications) calls.
 */
public interface EmailProvider {

    SmtpMailService.SmtpResult send(EmailMessage message);

    /** True if this provider has enough configuration to attempt a send at all. */
    boolean isConfigured();

    /** Short label used in SmtpResult.providerUsed() and logs. */
    String label();
}
