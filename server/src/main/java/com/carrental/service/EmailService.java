package com.carrental.service;

import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            case "fr" -> "Code de réinitialisation du mot de passe RentCar";
            case "ar" -> "رمز إعادة تعيين كلمة مرور RentCar";
            default   -> "RentCar Password Reset Code";
        };
        String body = buildPasswordResetCodeEmail(userName, code, expiresInMinutes, lang);
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
        String subject = "Your RentCar password was changed";
        String body = buildPasswordChangedEmail(userName);
        deliver(smtpMailService.sendPlatform(toEmail, subject, body), toEmail, subject);
    }

    /**
     * @deprecated Use {@link #sendPasswordResetCodeEmail} instead.
     * Kept for backward compatibility — sends a reset link.
     */
    @Deprecated
    public void sendPasswordResetEmail(String toEmail, String resetToken, String frontendUrl) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        String subject = "Password Reset Request";
        String body = buildPasswordResetEmail(resetLink);
        deliver(smtpMailService.sendPlatform(toEmail, subject, body), toEmail, subject);
    }

    /**
     * Sends an email verification email with a verification link.
     */
    public void sendVerificationEmail(String toEmail, String verificationToken, String frontendUrl) {
        String verifyLink = frontendUrl + "/verify-email?token=" + verificationToken;
        String subject = "Verify Your Email Address";
        String body = buildVerificationEmail(verifyLink);
        deliver(smtpMailService.sendPlatform(toEmail, subject, body), toEmail, subject);
    }

    /**
     * Sends a 6-digit email verification code (code-based flow for authenticated users).
     */
    public SmtpMailService.SmtpResult sendEmailVerificationCodeEmail(String toEmail, String userName, String code) {
        String subject = "Your Email Verification Code";
        String body = buildEmailVerificationCodeEmail(userName, code);
        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, body);
        deliver(result, toEmail, subject);
        return result;
    }

    /**
     * Sends a 6-digit Email OTP code for 2FA or security verification.
     * Throws if SMTP fails — caller (EmailOtpService) handles the exception.
     */
    public void sendEmailOtpCode(String toEmail, String userName, String code,
                                  int expiresInMinutes, Object purpose) {
        String subject = "Your RentCar verification code";
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
        String subject = "Welcome to RentCar SaaS";
        String body = buildWelcomeEmail(firstName);
        deliver(smtpMailService.sendPlatform(toEmail, subject, body), toEmail, subject);
    }

    /**
     * Sends "contract ready for signature" email to client.
     */
    public void sendContractReadyEmail(String toEmail, String clientName, String contractNumber,
                                        String signingUrl, String agencyName) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("[EMAIL] Skipped contract ready email — client has no email [contract={}]", contractNumber);
            return;
        }
        String subject = agencyName + " — Your Rental Contract is Ready for Signature";
        String body = buildContractReadyEmail(clientName, contractNumber, signingUrl, agencyName);
        // Client-facing emails should appear to come from the agency, so the
        // agency's own SMTP (if configured) takes priority here.
        deliver(smtpMailService.sendForTenant(TenantContext.getCurrentTenantId(), toEmail, subject, body),
                toEmail, subject);
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
        String body = buildContractSignedEmail(clientName, contractNumber, agencyName);
        deliver(smtpMailService.sendForTenant(TenantContext.getCurrentTenantId(), toEmail, subject, body),
                toEmail, subject);
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
                    "Nous avons reçu une demande de réinitialisation de votre mot de passe RentCar. Utilisez le code de vérification ci-dessous :",
                    "Ce code expire dans <strong>%d minutes</strong>. Ne le partagez avec personne.",
                    "Si vous n'avez pas demandé de réinitialisation, vous pouvez ignorer cet e-mail en toute sécurité. Votre mot de passe ne sera pas modifié.",
                    "&copy; 2025 RentCar SaaS — Innovacar. Tous droits réservés.");
            case "ar" -> new Copy("ar", "rtl", "مرحباً",
                    "إعادة تعيين كلمة المرور",
                    "تلقينا طلبًا لإعادة تعيين كلمة مرور حساب RentCar الخاص بك. استخدم رمز التحقق أدناه:",
                    "تنتهي صلاحية هذا الرمز خلال <strong>%d دقيقة</strong>. لا تشاركه مع أي شخص.",
                    "إذا لم تطلب إعادة تعيين كلمة المرور، يمكنك تجاهل هذا البريد الإلكتروني بأمان. لن يتم تغيير كلمة المرور الخاصة بك.",
                    "&copy; 2025 RentCar SaaS — Innovacar. جميع الحقوق محفوظة.");
            default -> new Copy("en", "ltr", "Hi",
                    "Reset your password",
                    "We received a request to reset your RentCar password. Use the verification code below:",
                    "This code expires in <strong>%d minutes</strong>. Do not share it with anyone.",
                    "If you did not request a password reset, you can safely ignore this email. Your password will not change.",
                    "&copy; 2025 RentCar SaaS — Innovacar. All rights reserved.");
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
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">RentCar</h1>
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
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">RentCar</h1>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">Password changed successfully</h2>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">Hi %s,</p>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                        Your RentCar account password was changed successfully.
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
                        &copy; 2025 RentCar SaaS. All rights reserved.
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

            You recently requested to reset your password for your RentCar account.
            Click the link below to reset it:

            %s

            This link will expire in 1 hour.
            If you did not request a password reset, please ignore this email.

            — RentCar Security Team
            """, resetLink);
    }

    private String buildEmailVerificationCodeEmail(String userName, String code) {
        String name = (userName != null && !userName.isBlank()) ? userName : "there";
        return """
            Verify Your Email Address
            =========================

            Hi %s,

            Your email verification code is:

                %s

            This code is valid for 10 minutes.
            Enter it in the app to verify your email address.

            If you did not request this, please ignore this email.

            — RentCar Security Team
            """.formatted(name, code);
    }

    private String buildVerificationEmail(String verifyLink) {
        return String.format("""
            Verify Your Email Address
            =========================

            Welcome to RentCar SaaS! Please verify your email address by clicking the link below:

            %s

            This link will expire in 24 hours.
            If you did not create an account, please ignore this email.

            — RentCar Team
            """, verifyLink);
    }

    private String buildWelcomeEmail(String firstName) {
        return String.format("""
            Welcome to RentCar SaaS, %s!
            ====================================

            Your account has been created successfully. You can now:
            - Manage your vehicle fleet
            - Track reservations and contracts
            - Handle payments and invoicing
            - Monitor GPS tracking

            Get started by logging in at your dashboard.

            — RentCar Team
            """, firstName != null ? firstName : "there");
    }

    private String buildContractReadyEmail(String clientName, String contractNumber,
                                            String signingUrl, String agencyName) {
        return String.format("""
            Dear %s,

            Your rental contract (%s) from %s is ready for your signature.

            Please click the link below to review and sign the contract:
            %s

            If you have any questions, please contact %s directly.

            — %s
            """,
            clientName != null ? clientName : "Valued Client",
            contractNumber,
            agencyName,
            signingUrl,
            agencyName,
            agencyName);
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
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">RentCar</h1>
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
                          <strong>Security tip:</strong> RentCar will never ask you to share this code via email, phone, or chat.
                        </p>
                      </div>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        &copy; 2025 RentCar &mdash; Innovacar. All rights reserved.
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name, purposeText, code, expiresInMinutes);
    }

    private String buildContractSignedEmail(String clientName, String contractNumber, String agencyName) {
        return String.format("""
            Dear %s,

            Great news! Your rental contract (%s) has been fully signed by both parties.

            The contract is now active and legally binding. A signed copy of the contract
            is available in your account.

            Thank you for choosing %s.

            — %s
            """,
            clientName != null ? clientName : "Valued Client",
            contractNumber,
            agencyName,
            agencyName);
    }
}
