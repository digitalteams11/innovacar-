package com.carrental.service;

import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Email service for sending transactional emails.
 * <p>
 * Delivery goes through {@link SmtpMailService}, which sends over whichever
 * SMTP provider is actually configured (tenant's own, falling back to the
 * platform-wide provider). A failed or unconfigured send is logged — it never
 * throws, so it can't break the business operation that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SmtpMailService smtpMailService;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final EmailActionUrlBuilder actionUrlBuilder;

    /** Builds a template-variable map, silently skipping any null values (Thymeleaf treats a missing key as null anyway). */
    private static Map<String, Object> vars(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            Object value = keyValuePairs[i + 1];
            if (value != null) map.put((String) keyValuePairs[i], value);
        }
        return map;
    }

    private static String displayName(String name) {
        return (name != null && !name.isBlank()) ? name : "there";
    }

    public void sendCustomerSuccessEmail(String toEmail, String subject, String message) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("[EMAIL] Skipped customer success email because no recipient was provided");
            return;
        }
        deliver(smtpMailService.sendForTenant(TenantContext.getCurrentTenantId(), toEmail, subject, message),
                toEmail, subject);
    }

    /**
     * Sends a 6-digit password reset code email (new code-based flow), localized to English.
     */
    public SmtpMailService.SmtpResult sendPasswordResetCodeEmail(String toEmail, String userName, String code, int expiresInMinutes) {
        return sendPasswordResetCodeEmail(toEmail, userName, code, expiresInMinutes, "en");
    }

    /**
     * Sends a 6-digit password reset code email (new code-based flow), localized to the
     * user's preferred language (en / fr / ar).
     */
    public SmtpMailService.SmtpResult sendPasswordResetCodeEmail(String toEmail, String userName, String code,
                                                                   int expiresInMinutes, String language) {
        String lang = normalizeLanguage(language);
        String subject = switch (lang) {
            case "fr" -> "Code de réinitialisation du mot de passe Innovacar";
            case "ar" -> "رمز إعادة تعيين كلمة مرور Innovacar";
            default   -> "Reset your Innovacar password";
        };
        // English renders through the new branded Thymeleaf template; fr/ar keep the
        // existing inline-HTML builder below (still de-branded) until those get their
        // own localized templates — not expanding template scope beyond what's asked.
        String body = "en".equals(lang)
                ? emailTemplateRenderer.render("reset-password", vars(
                        "firstName", displayName(userName), "code", code, "expiresInMinutes", expiresInMinutes))
                : buildPasswordResetCodeEmail(userName, code, expiresInMinutes, lang);
        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, body);
        deliver(result, toEmail, subject);
        return result;
    }

    private static String normalizeLanguage(String language) {
        if (language == null) return "en";
        String l = language.trim().toLowerCase();
        return switch (l) {
            case "fr", "ar" -> l;
            default -> "en";
        };
    }

    /**
     * Sends a confirmation email after a successful password reset.
     */
    public void sendPasswordChangedEmail(String toEmail, String userName) {
        String subject = "Your Innovacar password was changed";
        String body = buildPasswordChangedEmail(userName);
        deliver(smtpMailService.sendPlatform(toEmail, subject, body), toEmail, subject);
    }

    /**
     * @deprecated Use {@link #sendPasswordResetCodeEmail} instead.
     * Kept for backward compatibility — sends a reset link.
     */
    @Deprecated
    public void sendPasswordResetEmail(String toEmail, String resetToken, String frontendUrl) {
        String resetLink = actionUrlBuilder.passwordResetUrl(resetToken);
        String subject = "Password Reset Request";
        String body = buildPasswordResetEmail(resetLink);
        deliver(smtpMailService.sendPlatform(toEmail, subject, body), toEmail, subject);
    }

    /**
     * Sends an email verification email with a verification link.
     */
    public void sendVerificationEmail(String toEmail, String verificationToken, String frontendUrl) {
        sendVerificationEmail(toEmail, null, verificationToken, frontendUrl);
    }

    /**
     * Sends an email verification email with a verification link, addressed by name.
     */
    public void sendVerificationEmail(String toEmail, String firstName, String verificationToken, String frontendUrl) {
        String verifyLink = actionUrlBuilder.emailVerificationUrl(verificationToken);
        String subject = "Verify your email address - Innovacar";
        String htmlBody = emailTemplateRenderer.render("verify-email", vars(
                "firstName", displayName(firstName), "verificationUrl", verifyLink));
        String plainBody = buildVerificationEmailPlainText(verifyLink);
        deliver(smtpMailService.sendPlatform(toEmail, subject, htmlBody, plainBody), toEmail, subject);
    }

    /**
     * Sends a 6-digit email verification code (code-based flow for authenticated users),
     * valid for the default 10-minute window.
     */
    public SmtpMailService.SmtpResult sendEmailVerificationCodeEmail(String toEmail, String userName, String code) {
        return sendEmailVerificationCodeEmail(toEmail, userName, code, 10);
    }

    /**
     * Sends a 6-digit email verification code (code-based flow for authenticated users).
     * Rendered as a branded HTML email (via {@link BrandedEmailLayout}) with a real
     * plain-text fallback — both parts are sent explicitly so the HTML part is never
     * a raw plain-text string mislabeled as HTML.
     */
    public SmtpMailService.SmtpResult sendEmailVerificationCodeEmail(String toEmail, String userName, String code,
                                                                       int expirationMinutes) {
        String subject = "Verify your email address - Innovacar";
        String htmlBody = emailTemplateRenderer.render("verify-email", vars(
                "firstName", displayName(userName), "code", formatCodeForDisplay(code), "expiresInMinutes", expirationMinutes));
        String plainBody = buildEmailVerificationCodePlainText(userName, code, expirationMinutes);
        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, htmlBody, plainBody);
        deliver(result, toEmail, subject);
        return result;
    }

    /**
     * Sends a 6-digit Email OTP code for 2FA or security verification.
     * Throws if SMTP fails — caller (EmailOtpService) handles the exception.
     */
    public void sendEmailOtpCode(String toEmail, String userName, String code,
                                  int expiresInMinutes, Object purpose) {
        String subject = "Your Innovacar verification code";
        String purposeLabel = purpose != null ? purpose.toString() : "VERIFICATION";
        String body = buildEmailOtpCodeEmail(userName, code, expiresInMinutes, purposeLabel);
        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, body);
        if (!result.sent()) {
            String errorCode = result.errorCode() != null ? result.errorCode() : "EMAIL_SEND_FAILED";
            log.warn("[EMAIL] OTP send failed to {} | errorCode={} | message={}", toEmail, errorCode, result.errorMessage());
            throw new RuntimeException(errorCode);
        }
        deliver(result, toEmail, subject);
    }

    /**
     * Sends a welcome email after successful registration.
     */
    public void sendWelcomeEmail(String toEmail, String firstName) {
        sendWelcomeEmail(toEmail, firstName, null, null, actionUrlBuilder.dashboardUrl());
    }

    /**
     * Sends a welcome email after successful registration, including trial plan
     * details and a direct dashboard link when available.
     */
    public void sendWelcomeEmail(String toEmail, String firstName, String planName, String trialEndDate, String dashboardUrl) {
        String subject = "Welcome to Innovacar";
        String body = emailTemplateRenderer.render("welcome", vars(
                "firstName", displayName(firstName),
                "planName", planName,
                "trialEndDate", trialEndDate,
                "dashboardUrl", dashboardUrl));
        deliver(smtpMailService.sendPlatform(toEmail, subject, body), toEmail, subject);
    }

    /**
     * Sends "contract ready for signature" email to client, with the signing
     * link and (when available) a scannable QR code image attached.
     */
    public void sendContractReadyEmail(String toEmail, String clientName, String contractNumber,
                                        String signingUrl, String agencyName, byte[] qrPngBytes) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("[EMAIL] Skipped contract ready email — client has no email [contract={}]", contractNumber);
            return;
        }
        String subject = agencyName + " — Your Rental Contract is Ready for Signature";
        String htmlBody = buildContractReadyEmail(clientName, contractNumber, signingUrl, agencyName,
                qrPngBytes != null && qrPngBytes.length > 0);
        String plainBody = String.format(
                "Dear %s,%n%nYour rental contract (%s) from %s is ready for your signature.%n%n"
                + "Please open the link below to review and sign the contract:%n%s%n%n"
                + "If you have any questions, please contact %s directly.%n%n— %s",
                clientName != null ? clientName : "Valued Client", contractNumber, agencyName,
                signingUrl, agencyName, agencyName);
        boolean hasQr = qrPngBytes != null && qrPngBytes.length > 0;
        // Client-facing emails should appear to come from the agency, so the
        // agency's own SMTP (if configured) takes priority here.
        SmtpMailService.SmtpResult result = hasQr
                ? smtpMailService.sendForTenant(TenantContext.getCurrentTenantId(), toEmail, subject,
                        htmlBody, plainBody, "contract-signing-qr.png", qrPngBytes, "image/png")
                : smtpMailService.sendForTenant(TenantContext.getCurrentTenantId(), toEmail, subject,
                        htmlBody, plainBody);
        deliver(result, toEmail, subject);
    }

    /**
     * Sends the "please complete your information" email for the client
     * self-fill workflow (ClientInformationRequestService). Localized to the
     * client's preferred language, falling back to French, and sent via the
     * agency's own sender identity when configured (falls back to Innovacar).
     */
    public SmtpMailService.SmtpResult sendClientInformationRequestEmail(String toEmail, String clientName,
                                                                          String agencyName, String publicUrl,
                                                                          java.time.LocalDateTime expiresAt, String language) {
        String lang = normalizeLanguage(language);
        record Copy(String htmlLang, String dir, String subject, String heading, String greeting, String intro,
                    String expiryNote, String button, String fallbackLabel, String security) {}
        Copy c = switch (lang) {
            case "fr" -> new Copy("fr", "ltr",
                    "Veuillez compléter vos informations de location",
                    "Complétez vos informations",
                    "Bonjour",
                    "Votre agence de location vous invite à compléter vos informations afin de préparer votre dossier et votre contrat.",
                    "Ce lien expire le %s.",
                    "Compléter mes informations",
                    "Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :",
                    "Ce lien est personnel et sécurisé. Ne le partagez avec personne.");
            case "ar" -> new Copy("ar", "rtl",
                    "يرجى استكمال معلومات الحجز الخاصة بك",
                    "أكمل معلوماتك",
                    "مرحباً",
                    "تدعوك وكالة التأجير الخاصة بك لاستكمال معلوماتك من أجل تجهيز ملفك وعقدك.",
                    "تنتهي صلاحية هذا الرابط في %s.",
                    "أكمل معلوماتي",
                    "إذا لم يعمل الزر، انسخ هذا الرابط في متصفحك:",
                    "هذا الرابط شخصي وآمن. لا تشاركه مع أي شخص.");
            default -> new Copy("en", "ltr",
                    "Please complete your rental information",
                    "Complete your information",
                    "Hello",
                    "Your rental agency invites you to complete your information so they can prepare your file and contract.",
                    "This link expires on %s.",
                    "Complete my information",
                    "If the button doesn't work, copy this link into your browser:",
                    "This link is personal and secure. Do not share it with anyone.");
        };
        String name = StringUtils.hasText(clientName) ? clientName : (lang.equals("fr") ? "cher client" : lang.equals("ar") ? "عميلنا العزيز" : "there");
        String agency = StringUtils.hasText(agencyName) ? agencyName : "Innovacar";
        String expiryFormatted = expiresAt != null
                ? expiresAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "";

        String bodyHtml =
              "<h2 style=\"margin:0 0 16px;color:#0f172a;font-size:20px;\">" + escape(c.heading()) + "</h2>"
            + "<p style=\"margin:0 0 20px;color:#374151;font-size:15px;line-height:1.6;\">" + escape(c.greeting()) + " " + escape(name) + ",</p>"
            + "<p style=\"margin:0 0 20px;color:#374151;font-size:15px;line-height:1.6;\">" + escape(c.intro()) + "</p>"
            + BrandedEmailLayout.cta(c.button(), publicUrl)
            + "<p style=\"margin:0 0 8px;color:#6b7280;font-size:13px;line-height:1.6;\">" + escape(c.fallbackLabel()) + "</p>"
            + "<p style=\"margin:0 0 20px;word-break:break-all;\"><a href=\"" + publicUrl + "\" style=\"color:#0f766e;font-size:13px;\">" + publicUrl + "</a></p>"
            + BrandedEmailLayout.alertBox(c.expiryNote().formatted(expiryFormatted), c.security());
        String htmlBody = "<!DOCTYPE html><html lang=\"" + c.htmlLang() + "\" dir=\"" + c.dir() + "\">"
                + "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + escape(c.subject()) + "</title></head>"
                + "<body style=\"margin:0;padding:0;\">" + BrandedEmailLayout.shell(c.subject(), null, bodyHtml, agency, "support@innovacar.app") + "</body></html>";

        String plainBody = c.greeting() + " " + name + ",\n\n" + c.intro() + "\n\n" + publicUrl + "\n\n" + c.expiryNote().formatted(expiryFormatted);

        SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(TenantContext.getCurrentTenantId(), toEmail, c.subject(), htmlBody, plainBody);
        deliver(result, toEmail, c.subject());
        return result;
    }

    /**
     * Sends "contract fully signed" confirmation email to client.
     */
    public void sendContractSignedEmail(String toEmail, String clientName, String contractNumber,
                                         String agencyName) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("[EMAIL] Skipped contract signed email — client has no email [contract={}]", contractNumber);
            return;
        }
        String subject = agencyName + " — Your Contract Has Been Fully Signed";
        String body = emailTemplateRenderer.render("contract-signed", vars(
                "clientName", displayName(clientName), "contractNumber", contractNumber, "agencyName", agencyName));
        deliver(smtpMailService.sendForTenant(TenantContext.getCurrentTenantId(), toEmail, subject, body),
                toEmail, subject);
    }

    /**
     * Sends the additional-driver signature-request email. Modeled on
     * {@link #sendClientInformationRequestEmail}: localized FR/EN/AR (French
     * fallback per product decision — the client-information-request variant
     * above falls back to English instead), branded shell, returns the real
     * {@link SmtpMailService.SmtpResult} so the caller (AdditionalDriverSigningService)
     * can gate signatureStatus/deliveryStatus on actual provider acceptance
     * instead of assuming success.
     */
    public SmtpMailService.SmtpResult sendAdditionalDriverSignatureEmail(
            String toEmail, String driverName, String contractNumber, String signingUrl,
            String agencyName, String vehicleSummary, java.time.LocalDate startDate, java.time.LocalDate endDate,
            java.time.LocalDateTime expiresAt, String language) {
        String lang = normalizeLanguageFrFallback(language);
        record Copy(String htmlLang, String dir, String subject, String heading, String greeting, String roleLabel,
                    String intro, String vehicleLabel, String datesLabel, String expiryNote, String button,
                    String fallbackLabel, String security) {}
        Copy c = switch (lang) {
            case "en" -> new Copy("en", "ltr",
                    "Signature required — Contract " + contractNumber,
                    "Signature required",
                    "Hello",
                    "additional driver",
                    "You have been added as an additional driver on rental contract %s from %s. Please review and sign below.",
                    "Vehicle",
                    "Rental period",
                    "This link expires on %s.",
                    "Review and sign",
                    "If the button doesn't work, copy this link into your browser:",
                    "This link is personal and secure. Do not share it with anyone.");
            case "ar" -> new Copy("ar", "rtl",
                    "التوقيع مطلوب — العقد " + contractNumber,
                    "التوقيع مطلوب",
                    "مرحباً",
                    "سائق إضافي",
                    "لقد تمت إضافتك كسائق إضافي في عقد الإيجار %s الخاص بـ %s. يرجى الاطلاع والتوقيع أدناه.",
                    "المركبة",
                    "فترة الإيجار",
                    "تنتهي صلاحية هذا الرابط في %s.",
                    "الاطلاع والتوقيع",
                    "إذا لم يعمل الزر، انسخ هذا الرابط في متصفحك:",
                    "هذا الرابط شخصي وآمن. لا تشاركه مع أي شخص.");
            default -> new Copy("fr", "ltr",
                    "Signature requise — Contrat " + contractNumber,
                    "Signature requise",
                    "Bonjour",
                    "conducteur additionnel",
                    "Vous avez été ajouté comme conducteur additionnel sur le contrat de location %s de %s. Veuillez consulter et signer ci-dessous.",
                    "Véhicule",
                    "Période de location",
                    "Ce lien expire le %s.",
                    "Consulter et signer",
                    "Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :",
                    "Ce lien est personnel et sécurisé. Ne le partagez avec personne.");
        };

        String name = StringUtils.hasText(driverName) ? driverName
                : (lang.equals("fr") ? "cher conducteur" : lang.equals("ar") ? "عزيزنا السائق" : "there");
        String agency = StringUtils.hasText(agencyName) ? agencyName : "Innovacar";
        String expiryFormatted = expiresAt != null
                ? expiresAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "";
        String datesRange = (startDate != null ? startDate.toString() : "?") + " → " + (endDate != null ? endDate.toString() : "?");

        java.util.List<String> infoRows = new java.util.ArrayList<>();
        infoRows.add("<strong>" + escape(c.vehicleLabel()) + ":</strong> " + escape(StringUtils.hasText(vehicleSummary) ? vehicleSummary : "—"));
        infoRows.add("<strong>" + escape(c.datesLabel()) + ":</strong> " + escape(datesRange));

        String bodyHtml =
              "<h2 style=\"margin:0 0 16px;color:#0f172a;font-size:20px;\">" + escape(c.heading()) + "</h2>"
            + "<p style=\"margin:0 0 8px;color:#374151;font-size:15px;line-height:1.6;\">" + escape(c.greeting()) + " " + escape(name) + ",</p>"
            + "<p style=\"margin:0 0 20px;color:#374151;font-size:15px;line-height:1.6;\">"
                + escape(c.intro()).formatted(escape(contractNumber), escape(agency)) + "</p>"
            + BrandedEmailLayout.infoBox(infoRows.toArray(new String[0]))
            + BrandedEmailLayout.cta(c.button(), signingUrl)
            + "<p style=\"margin:0 0 8px;color:#6b7280;font-size:13px;line-height:1.6;\">" + escape(c.fallbackLabel()) + "</p>"
            + "<p style=\"margin:0 0 20px;word-break:break-all;\"><a href=\"" + signingUrl + "\" style=\"color:#0f766e;font-size:13px;\">" + signingUrl + "</a></p>"
            + BrandedEmailLayout.alertBox(c.expiryNote().formatted(expiryFormatted), c.security());
        String htmlBody = "<!DOCTYPE html><html lang=\"" + c.htmlLang() + "\" dir=\"" + c.dir() + "\">"
                + "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + escape(c.subject()) + "</title></head>"
                + "<body style=\"margin:0;padding:0;\">" + BrandedEmailLayout.shell(c.subject(), null, bodyHtml, agency, "support@innovacar.app") + "</body></html>";

        String plainBody = c.greeting() + " " + name + ",\n\n" + c.intro().formatted(contractNumber, agency)
                + "\n\n" + signingUrl + "\n\n" + c.expiryNote().formatted(expiryFormatted);

        SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(
                TenantContext.getCurrentTenantId(), toEmail, c.subject(), htmlBody, plainBody);
        deliver(result, toEmail, c.subject());
        return result;
    }

    /** Same 3-language set as {@link #normalizeLanguage}, but defaults to French — the product decision for this specific email. */
    private static String normalizeLanguageFrFallback(String language) {
        if (language == null) return "fr";
        String l = language.trim().toLowerCase();
        return switch (l) {
            case "fr", "en", "ar" -> l;
            default -> "fr";
        };
    }

    private void deliver(SmtpMailService.SmtpResult result, String toEmail, String subject) {
        if (result.sent()) {
            log.info("[EMAIL] Delivered via {} to {} | Subject: {}", result.providerUsed(), toEmail, subject);
        } else {
            log.warn("[EMAIL] Not delivered to {} | Subject: {} | Reason: {}", toEmail, subject, result.errorMessage());
        }
    }

    // ── Email templates ─────────────────────────────────────────────────────

    private String buildPasswordResetCodeEmail(String userName, String code, int expiresInMinutes, String lang) {
        record Copy(String htmlLang, String dir, String greetingName, String heading, String intro,
                    String expiry, String ignore, String footer) {}
        Copy c = switch (lang) {
            case "fr" -> new Copy("fr", "ltr", "Bonjour",
                    "Réinitialisez votre mot de passe",
                    "Nous avons reçu une demande de réinitialisation de votre mot de passe Innovacar. Utilisez le code de vérification ci-dessous :",
                    "Ce code expire dans <strong>%d minutes</strong>. Ne le partagez avec personne.",
                    "Si vous n'avez pas demandé de réinitialisation, vous pouvez ignorer cet e-mail en toute sécurité. Votre mot de passe ne sera pas modifié.",
                    "&copy; 2025 Innovacar. Tous droits réservés.");
            case "ar" -> new Copy("ar", "rtl", "مرحباً",
                    "إعادة تعيين كلمة المرور",
                    "تلقينا طلبًا لإعادة تعيين كلمة مرور حساب Innovacar الخاص بك. استخدم رمز التحقق أدناه:",
                    "تنتهي صلاحية هذا الرمز خلال <strong>%d دقيقة</strong>. لا تشاركه مع أي شخص.",
                    "إذا لم تطلب إعادة تعيين كلمة المرور، يمكنك تجاهل هذا البريد الإلكتروني بأمان. لن يتم تغيير كلمة المرور الخاصة بك.",
                    "&copy; 2025 Innovacar. جميع الحقوق محفوظة.");
            default -> new Copy("en", "ltr", "Hi",
                    "Reset your password",
                    "We received a request to reset your Innovacar password. Use the verification code below:",
                    "This code expires in <strong>%d minutes</strong>. Do not share it with anyone.",
                    "If you did not request a password reset, you can safely ignore this email. Your password will not change.",
                    "&copy; 2025 Innovacar. All rights reserved.");
        };
        String name = (userName != null && !userName.isBlank()) ? userName : (lang.equals("fr") ? "cher utilisateur" : lang.equals("ar") ? "عزيزي المستخدم" : "there");
        String expiryLine = c.expiry().formatted(expiresInMinutes);
        return """
            <!DOCTYPE html>
            <html lang="%s" dir="%s">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                    <tr><td style="background:#1a56db;padding:32px 40px;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">Innovacar</h1>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">%s</h2>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">%s %s,</p>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                        %s
                      </p>
                      <div style="background:#f0f4ff;border:2px dashed #1a56db;border-radius:8px;padding:24px;text-align:center;margin:0 0 24px;">
                        <span style="font-size:42px;font-weight:700;letter-spacing:12px;color:#1a56db;font-family:monospace;">%s</span>
                      </div>
                      <p style="margin:0 0 24px;color:#6b7280;font-size:14px;line-height:1.6;">
                        %s
                      </p>
                      <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.6;">
                        %s
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        %s
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(c.htmlLang(), c.dir(), c.heading(), c.greetingName(), name, c.intro(), code, expiryLine, c.ignore(), c.footer());
    }

    private String buildPasswordChangedEmail(String userName) {
        String name = (userName != null && !userName.isBlank()) ? userName : "there";
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                    <tr><td style="background:#059669;padding:32px 40px;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">Innovacar</h1>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">Password changed successfully</h2>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">Hi %s,</p>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                        Your Innovacar account password was changed successfully.
                      </p>
                      <div style="background:#f0fdf4;border-left:4px solid #059669;padding:16px 20px;border-radius:4px;margin:0 0 24px;">
                        <p style="margin:0;color:#065f46;font-size:14px;">
                          &#10003; Password updated on %s
                        </p>
                      </div>
                      <p style="margin:0;color:#6b7280;font-size:14px;line-height:1.6;">
                        If you did not make this change, please contact our support team immediately and secure your account.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        &copy; 2025 Innovacar. All rights reserved.
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name, java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm")));
    }

    private String buildPasswordResetEmail(String resetLink) {
        return String.format("""
            Password Reset Request
            ======================

            You recently requested to reset your password for your Innovacar account.
            Click the link below to reset it:

            %s

            This link will expire in 1 hour.
            If you did not request a password reset, please ignore this email.

            — Innovacar Security Team
            """, resetLink);
    }

    private String buildEmailVerificationCodePlainText(String userName, String code, int expirationMinutes) {
        String name = (userName != null && !userName.isBlank()) ? userName : "there";
        return """
            Verify your email address - Innovacar

            Hi %s,

            Use the code below to verify your email address and finish setting up your Innovacar account:

                %s

            This code is valid for %d minutes. Enter it in the app to verify your email address.

            Security note: if you did not request this, you can safely ignore this email.

            Need help? Contact support@innovacar.app
            — Innovacar by Innovax Technologies
            """.formatted(name, code, expirationMinutes);
    }

    private String buildVerificationEmailPlainText(String verifyLink) {
        return """
            Verify your email address - Innovacar

            Welcome to Innovacar! Please verify your email address by opening the link below:

            %s

            This link will expire in 24 hours.
            If you did not create an account, please ignore this email.

            Need help? Contact support@innovacar.app
            — Innovacar by Innovax Technologies
            """.formatted(verifyLink);
    }

    /** Splits a numeric code into "271 888"-style groups of 3 for readability; falls back to the raw code otherwise. */
    private static String formatCodeForDisplay(String code) {
        if (code == null || !code.matches("\\d{6}")) return code;
        return code.substring(0, 3) + " " + code.substring(3);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildContractReadyEmail(String clientName, String contractNumber,
                                            String signingUrl, String agencyName, boolean hasQrAttachment) {
        String name = clientName != null && !clientName.isBlank() ? clientName : "Valued Client";
        String qrNote = hasQrAttachment
                ? "<p style=\"margin:0 0 24px;color:#6b7280;font-size:14px;line-height:1.6;\">"
                + "You can also scan the QR code attached to this email with your phone to open the signing page.</p>"
                : "";
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                    <tr><td style="background:#1a56db;padding:32px 40px;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">%s</h1>
                      <p style="margin:6px 0 0;color:#93c5fd;font-size:13px;">Rental Contract Signature Required</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">Your contract is ready to sign</h2>
                      <p style="margin:0 0 8px;color:#374151;font-size:15px;line-height:1.6;">Dear %s,</p>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                        Your rental contract <strong>%s</strong> from %s is ready for your signature.
                      </p>
                      <div style="text-align:center;margin:0 0 24px;">
                        <a href="%s" style="display:inline-block;background:#1a56db;color:#ffffff;text-decoration:none;font-size:15px;font-weight:700;padding:14px 32px;border-radius:8px;">Review &amp; Sign Contract</a>
                      </div>
                      <p style="margin:0 0 24px;color:#6b7280;font-size:13px;line-height:1.6;word-break:break-all;">
                        Or copy this link into your browser: <a href="%s" style="color:#1a56db;">%s</a>
                      </p>
                      %s
                      <p style="margin:0 0 8px;color:#6b7280;font-size:14px;line-height:1.6;">
                        If you have any questions, please contact %s directly.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        &mdash; %s
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(agencyName, name, contractNumber, agencyName, signingUrl, signingUrl, signingUrl,
                qrNote, agencyName, agencyName);
    }

    private String buildEmailOtpCodeEmail(String userName, String code, int expiresInMinutes, String purpose) {
        String name = (userName != null && !userName.isBlank()) ? userName : "there";
        String purposeText = switch (purpose) {
            case "LOGIN_2FA"        -> "sign in to your account";
            case "ENABLE_EMAIL_OTP" -> "enable Email OTP verification";
            case "PASSWORD_CHANGE"  -> "confirm your password change";
            case "DATA_RESET"       -> "authorize a data reset";
            default                 -> "verify your identity";
        };
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                    <tr><td style="background:#1a56db;padding:32px 40px;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">Innovacar</h1>
                      <p style="margin:6px 0 0;color:#93c5fd;font-size:13px;">Secure Verification Code</p>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">Your verification code</h2>
                      <p style="margin:0 0 8px;color:#374151;font-size:15px;line-height:1.6;">Hi %s,</p>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                        Use the code below to %s. Do not share this code with anyone.
                      </p>
                      <div style="background:#f0f4ff;border:2px dashed #1a56db;border-radius:8px;padding:28px;text-align:center;margin:0 0 24px;">
                        <span style="font-size:48px;font-weight:700;letter-spacing:16px;color:#1a56db;font-family:monospace;">%s</span>
                      </div>
                      <p style="margin:0 0 8px;color:#6b7280;font-size:14px;line-height:1.6;">
                        This code expires in <strong>%d minutes</strong>.
                      </p>
                      <p style="margin:0 0 24px;color:#6b7280;font-size:14px;line-height:1.6;">
                        If you did not request this code, please ignore this email and secure your account immediately.
                      </p>
                      <div style="background:#fef3c7;border-left:4px solid #f59e0b;border-radius:4px;padding:12px 16px;">
                        <p style="margin:0;color:#92400e;font-size:13px;line-height:1.5;">
                          <strong>Security tip:</strong> Innovacar will never ask you to share this code via email, phone, or chat.
                        </p>
                      </div>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        &copy; 2025 Innovacar. All rights reserved.
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name, purposeText, code, expiresInMinutes);
    }

}
