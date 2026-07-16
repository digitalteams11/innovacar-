package com.carrental.service;

import com.carrental.entity.PlatformSettings;
import com.carrental.entity.TenantSettings;
import com.carrental.repository.PlatformSettingsRepository;
import com.carrental.repository.TenantSettingsRepository;
import com.carrental.util.EncryptionUtil;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Properties;

/**
 * The one canonical email entry point — every feature (test email,
 * forgot-password, contract signing links, contract PDF notifications,
 * reservation/payment/trial notifications, support emails) calls this class,
 * never a JavaMailSender or an HTTP client directly.
 *
 * <p>Two delivery mechanisms exist: SMTP (a tenant's own SMTP from
 * {@link TenantSettings}, falling back to the platform-wide SMTP configured
 * by Super Admin in {@link PlatformSettings}) and an HTTPS email API
 * ({@link HttpEmailProvider}) for when the hosting network blocks outbound
 * SMTP ports entirely. {@code app.email.provider} (env var EMAIL_PROVIDER)
 * selects which one is authoritative — SMTP by default. The other is only
 * ever tried as a fallback, and only when {@code app.email.allow-provider-fallback}
 * (EMAIL_ALLOW_PROVIDER_FALLBACK) is explicitly set to true; providers are
 * never silently swapped.
 *
 * <p>Email is always a best-effort side effect: callers receive a result
 * record instead of an exception so a failed send never breaks the business
 * operation (e.g. contract creation) that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpMailService {

    private final PlatformSettingsRepository platformSettingsRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final EncryptionUtil encryptionUtil;
    private final HttpEmailProvider httpEmailProvider;

    @Value("${app.email.provider:SMTP}")
    private String activeProvider;

    @Value("${app.email.allow-provider-fallback:false}")
    private boolean allowProviderFallback;

    public record SmtpResult(boolean sent, String providerUsed, String errorMessage, String errorCode,
                              String exceptionClass) {
        public static SmtpResult unconfigured() {
            return new SmtpResult(false, null, "No SMTP provider is configured.", "SMTP_NOT_CONFIGURED", null);
        }
        public static SmtpResult failure(String provider, String message, String code) {
            return new SmtpResult(false, provider, message, code, null);
        }
        public static SmtpResult failure(String provider, String message, String code, String exceptionClass) {
            return new SmtpResult(false, provider, message, code, exceptionClass);
        }
        public static SmtpResult success(String provider) {
            return new SmtpResult(true, provider, null, null, null);
        }
    }

    private record SmtpConfig(String host, int port, String username, String password,
                               boolean tls, boolean ssl, String fromEmail, String fromName, String label) {
    }

    /** Non-secret snapshot of the platform SMTP config — safe to log (never includes the password). */
    public record PlatformSmtpDiagnostics(boolean configured, String source, String host, Integer port,
                                           boolean usernamePresent, String fromEmail, String fromName,
                                           boolean startTls, boolean passwordPresent) {
        public static PlatformSmtpDiagnostics none() {
            return new PlatformSmtpDiagnostics(false, "NONE", null, null, false, null, null, false, false);
        }
    }

    /** Describes the currently resolved platform SMTP config for safe debug logging. */
    @Transactional(readOnly = true)
    public PlatformSmtpDiagnostics describePlatformConfig() {
        return platformSettingsRepository.findTopByOrderByIdAsc()
                .map(settings -> new PlatformSmtpDiagnostics(
                        StringUtils.hasText(settings.getSmtpHost())
                                && StringUtils.hasText(settings.getSmtpUsername())
                                && settings.getSmtpPasswordEncrypted() != null
                                && !Boolean.FALSE.equals(settings.getSmtpEnabled()),
                        "DATABASE",
                        settings.getSmtpHost(),
                        settings.getSmtpPort() != null ? settings.getSmtpPort() : 587,
                        StringUtils.hasText(settings.getSmtpUsername()),
                        settings.getFromEmail(),
                        settings.getFromName(),
                        settings.getSmtpUseTls() == null || settings.getSmtpUseTls(),
                        settings.getSmtpPasswordEncrypted() != null))
                .orElse(PlatformSmtpDiagnostics.none());
    }

    /**
     * Sends using the tenant's own SMTP (falls back to platform SMTP).
     * Body is treated as HTML; a plain-text fallback is auto-generated by stripping tags.
     */
    @Transactional(readOnly = true)
    public SmtpResult sendForTenant(Long tenantId, String to, String subject, String body) {
        return send(() -> resolveTenantOrPlatformConfig(tenantId), to, subject, body, null);
    }

    /**
     * Sends using the tenant's own SMTP with explicit HTML + plain-text bodies.
     */
    @Transactional(readOnly = true)
    public SmtpResult sendForTenant(Long tenantId, String to, String subject,
                                    String htmlBody, String plainBody) {
        return send(() -> resolveTenantOrPlatformConfig(tenantId), to, subject, htmlBody, plainBody);
    }

    /**
     * Sends using the tenant's own SMTP with explicit HTML + plain-text bodies and a
     * single file attachment (e.g. the signed contract PDF).
     */
    @Transactional(readOnly = true)
    public SmtpResult sendForTenant(Long tenantId, String to, String subject,
                                    String htmlBody, String plainBody,
                                    String attachmentName, byte[] attachmentBytes, String attachmentContentType) {
        return send(() -> resolveTenantOrPlatformConfig(tenantId), to, subject, htmlBody, plainBody,
                attachmentName, attachmentBytes, attachmentContentType);
    }

    /**
     * Sends using only the platform-wide SMTP.
     * Body is treated as HTML; plain-text fallback auto-generated.
     */
    @Transactional(readOnly = true)
    public SmtpResult sendPlatform(String to, String subject, String body) {
        return send(this::resolvePlatformConfig, to, subject, body, null);
    }

    /**
     * Sends using only the platform-wide SMTP with explicit HTML + plain-text bodies.
     */
    @Transactional(readOnly = true)
    public SmtpResult sendPlatform(String to, String subject, String htmlBody, String plainBody) {
        return send(this::resolvePlatformConfig, to, subject, htmlBody, plainBody);
    }

    private SmtpConfig resolveTenantOrPlatformConfig(Long tenantId) {
        SmtpConfig config = resolveTenantConfig(tenantId);
        return config != null ? config : resolvePlatformConfig();
    }

    /** Returns true if the platform SMTP is usable. */
    public boolean isPlatformConfigured() {
        return resolvePlatformConfig() != null;
    }

    /** The provider value exactly as configured (EMAIL_PROVIDER) — safe to expose, no secrets. */
    public String activeProvider() {
        return activeProvider;
    }

    // ── Core sender — provider dispatch ─────────────────────────────────────────

    private SmtpResult send(java.util.function.Supplier<SmtpConfig> configSupplier, String to, String subject,
                            String htmlBody, String plainBody) {
        return send(configSupplier, to, subject, htmlBody, plainBody, null, null, null);
    }

    /**
     * Dispatches to whichever provider is authoritative
     * ({@code app.email.provider} / EMAIL_PROVIDER, SMTP by default). The other
     * mechanism is only ever tried as a fallback, and only when
     * {@code app.email.allow-provider-fallback} (EMAIL_ALLOW_PROVIDER_FALLBACK)
     * is explicitly true — providers are never silently swapped.
     *
     * <p>{@code configSupplier} is lazy — resolving the SMTP config decrypts a
     * stored password and hits the database, so when the HTTP provider is
     * authoritative and succeeds (or fallback is disabled), SMTP config is
     * never resolved at all.
     */
    private SmtpResult send(java.util.function.Supplier<SmtpConfig> configSupplier, String to, String subject,
                            String htmlBody, String plainBody,
                            String attachmentName, byte[] attachmentBytes, String attachmentContentType) {
        EmailMessage email = new EmailMessage(to, subject, htmlBody, plainBody,
                attachmentName, attachmentBytes, attachmentContentType);

        boolean httpProviderSelected = "ZEPTOMAIL".equalsIgnoreCase(activeProvider)
                || "ZOHO_API".equalsIgnoreCase(activeProvider);

        if (httpProviderSelected) {
            SmtpResult httpResult = httpEmailProvider.send(email);
            if (httpResult.sent() || !allowProviderFallback) {
                return httpResult;
            }
            log.warn("[EMAIL] HTTP provider ({}) failed and EMAIL_ALLOW_PROVIDER_FALLBACK=true — falling back to SMTP for {}",
                    activeProvider, to);
            return sendViaSmtp(configSupplier.get(), email);
        }

        SmtpResult smtpResult = sendViaSmtp(configSupplier.get(), email);
        if (!smtpResult.sent() && allowProviderFallback && httpEmailProvider.isConfigured()) {
            log.warn("[EMAIL] SMTP failed ({}) and EMAIL_ALLOW_PROVIDER_FALLBACK=true — falling back to HTTP provider for {}",
                    smtpResult.errorCode(), to);
            return httpEmailProvider.send(email);
        }
        return smtpResult;
    }

    private SmtpResult sendViaSmtp(SmtpConfig config, EmailMessage email) {
        if (config == null) {
            return SmtpResult.unconfigured();
        }
        if (!StringUtils.hasText(email.to())) {
            return SmtpResult.failure(config.label(), "Recipient email is required.", "EMAIL_NO_RECIPIENT");
        }
        // Zoho rejects (or silently rewrites) a From address that doesn't match the
        // authenticated mailbox unless it's a configured alias — catch this up front
        // instead of letting it surface as an opaque SMTP error after a real connection.
        String effectiveFrom = StringUtils.hasText(config.fromEmail()) ? config.fromEmail() : config.username();
        if (config.host() != null && config.host().toLowerCase().contains("zoho")
                && StringUtils.hasText(config.username())
                && !config.username().equalsIgnoreCase(effectiveFrom)) {
            return SmtpResult.failure(config.label(),
                    "From email (" + effectiveFrom + ") does not match the authenticated SMTP account (" + config.username() + ").",
                    "SMTP_FROM_EMAIL_INVALID");
        }
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            // Use exactly the host Super Admin (or the tenant) saved — never silently
            // swap it for a "more reliable" alternative. Zoho account provisioning is
            // genuinely per-host, so overriding it here would send through a
            // different configuration than the one that was actually configured,
            // tested, and saved.
            sender.setHost(config.host());
            sender.setPort(config.port());
            sender.setUsername(config.username());
            sender.setPassword(config.password());
            sender.setDefaultEncoding("UTF-8");

            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            if (config.ssl()) {
                // Implicit SSL (port 465): no STARTTLS negotiation, TLS from the first byte.
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.starttls.enable", "false");
                props.put("mail.smtp.starttls.required", "false");
            } else {
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.starttls.enable", String.valueOf(config.tls()));
                if (config.tls()) {
                    props.put("mail.smtp.starttls.required", "true");
                }
            }
            // Trust only the host actually being connected to — never a wildcard
            // or a list of alternate hosts not actually in use for this send.
            props.put("mail.smtp.ssl.trust", config.host());
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            boolean hasAttachment = email.hasAttachment();
            // MimeMessageHelper.setText(plain, html) requires multipart mode even without
            // an attachment — it builds a multipart/alternative body. Non-multipart mode
            // only works for a single plain-text or single HTML part.
            boolean needsMultipart = hasAttachment || StringUtils.hasText(email.htmlBody());

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, needsMultipart, "UTF-8");
            helper.setTo(email.to());
            if (StringUtils.hasText(config.fromName())) {
                helper.setFrom(effectiveFrom, config.fromName());
            } else {
                helper.setFrom(effectiveFrom);
            }
            helper.setSubject(email.subject() != null ? email.subject() : "");

            boolean hasHtml  = StringUtils.hasText(email.htmlBody());
            boolean hasPlain = StringUtils.hasText(email.plainBody());

            if (hasHtml) {
                // Multipart/alternative: plain-text fallback first, HTML second
                String plain = hasPlain ? email.plainBody() : stripHtml(email.htmlBody());
                helper.setText(plain, email.htmlBody());
            } else if (hasPlain) {
                helper.setText(email.plainBody(), false);
            } else {
                helper.setText("(no content)", false);
            }

            if (hasAttachment) {
                helper.addAttachment(email.attachmentName(), new org.springframework.core.io.ByteArrayResource(email.attachmentBytes()),
                        StringUtils.hasText(email.attachmentContentType()) ? email.attachmentContentType() : "application/pdf");
            }

            sender.send(message);
            log.info("[SMTP] Sent via {} to {} | Subject: {}{}", config.label(), email.to(), email.subject(),
                    hasAttachment ? " | Attachment: " + email.attachmentName() : "");
            return SmtpResult.success(config.label());
        } catch (Exception ex) {
            String errorCode = classifySmtpException(ex);
            log.warn("[SMTP] Send via {} to {} failed [{}]: exceptionClass={} exceptionMessage={}",
                    config.label(), email.to(), errorCode, ex.getClass().getName(), ex.getMessage());
            return SmtpResult.failure(config.label(), ex.getMessage(), errorCode, ex.getClass().getName());
        }
    }

    /** Strips HTML tags to produce a plain-text fallback for multipart emails. */
    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html
                // Remove script and style blocks entirely
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                // Convert common block elements to newlines
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n")
                // Strip remaining tags
                .replaceAll("<[^>]+>", "")
                // Decode common HTML entities
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;",  "<")
                .replace("&gt;",  ">")
                .replace("&quot;","\"")
                .replace("&#39;", "'")
                // Collapse excessive whitespace
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n[ \\t]+", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }


    private String classifySmtpException(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String type = ex.getClass().getSimpleName().toLowerCase();
        if (type.contains("authentication") || msg.contains("535") || msg.contains("authentication")
                || msg.contains("auth") || msg.contains("credentials") || msg.contains("invalid login")
                || msg.contains("username and password not accepted") || msg.contains("invalid credentials")) {
            return "EMAIL_AUTH_FAILED";
        }
        if (msg.contains("sender address rejected") || msg.contains("553") || msg.contains("501")
                || msg.contains("not allowed to send") || msg.contains("does not match authenticated user")
                || msg.contains("relay not permitted") || msg.contains("from address")) {
            return "EMAIL_FROM_REJECTED";
        }
        if (msg.contains("tls") || msg.contains("ssl") || msg.contains("handshake")) {
            return "EMAIL_TLS_FAILED";
        }
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "EMAIL_PROVIDER_TIMEOUT";
        }
        if (msg.contains("unknown host") || msg.contains("nodename nor servname")
                || msg.contains("unreachable") || msg.contains("connection refused")
                || msg.contains("connect")) {
            return "EMAIL_PROVIDER_UNREACHABLE";
        }
        return "EMAIL_SEND_FAILED";
    }

    private SmtpConfig resolveTenantConfig(Long tenantId) {
        if (tenantId == null) return null;
        return tenantSettingsRepository.findByTenantId(tenantId)
                .filter(settings -> StringUtils.hasText(settings.getSmtpHost())
                        && StringUtils.hasText(settings.getSmtpUsername())
                        && settings.getSmtpPasswordEncrypted() != null)
                .map(settings -> {
                    // tryDecrypt (not decrypt): a decrypt failure here — e.g. because
                    // APP_ENCRYPTION_SECRET changed since the password was stored — must
                    // never crash the caller (e.g. a public forgot-password request) with
                    // a 500. Treat it the same as "not configured" instead.
                    String password = encryptionUtil.tryDecrypt(settings.getSmtpPasswordEncrypted());
                    if (password == null) {
                        log.warn("[SMTP] Tenant [id={}] SMTP password could not be decrypted — treating as unconfigured", tenantId);
                        return null;
                    }
                    return new SmtpConfig(
                            settings.getSmtpHost(),
                            settings.getSmtpPort() != null ? settings.getSmtpPort() : 587,
                            settings.getSmtpUsername(),
                            password,
                            settings.getSmtpTls() == null || settings.getSmtpTls(),
                            false,
                            settings.getSmtpUsername(),
                            null,
                            "tenant SMTP");
                })
                .orElse(null);
    }

    private SmtpConfig resolvePlatformConfig() {
        return platformSettingsRepository.findTopByOrderByIdAsc()
                .filter(settings -> StringUtils.hasText(settings.getSmtpHost())
                        && StringUtils.hasText(settings.getSmtpUsername())
                        && settings.getSmtpPasswordEncrypted() != null
                        // Treat null as enabled (backward-compatible with pre-V24 rows);
                        // explicit FALSE means the Super Admin deliberately disabled platform email.
                        && !Boolean.FALSE.equals(settings.getSmtpEnabled()))
                .map(settings -> {
                    String password = encryptionUtil.tryDecrypt(settings.getSmtpPasswordEncrypted());
                    if (password == null) {
                        log.warn("[SMTP] Platform SMTP password could not be decrypted — treating as unconfigured");
                        return null;
                    }
                    return new SmtpConfig(
                            settings.getSmtpHost(),
                            settings.getSmtpPort() != null ? settings.getSmtpPort() : 587,
                            settings.getSmtpUsername(),
                            password,
                            settings.getSmtpUseTls() == null || settings.getSmtpUseTls(),
                            Boolean.TRUE.equals(settings.getSmtpSslEnabled()),
                            StringUtils.hasText(settings.getFromEmail()) ? settings.getFromEmail() : settings.getSmtpUsername(),
                            StringUtils.hasText(settings.getFromName()) ? settings.getFromName() : "RentCar",
                            "platform SMTP");
                })
                .orElse(null);
    }
}
