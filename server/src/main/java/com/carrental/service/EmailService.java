package com.carrental.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Email service for sending transactional emails.
 * <p>
 * In production, this should integrate with an SMTP provider (SendGrid, AWS SES, etc.)
 * or use Spring Mail. For now, it logs the emails and provides templates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    public void sendCustomerSuccessEmail(String toEmail, String subject, String message) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("[EMAIL] Skipped customer success email because no recipient was provided");
            return;
        }
        log.info("[EMAIL] To: {} | Subject: {}", toEmail, subject);
        log.info("[EMAIL BODY]\n{}", message);
    }

    /**
     * Sends a password reset email with a reset link.
     */
    public void sendPasswordResetEmail(String toEmail, String resetToken, String frontendUrl) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        String subject = "Password Reset Request";
        String body = buildPasswordResetEmail(resetLink);

        // In production, use JavaMailSender or an email API
        log.info("[EMAIL] To: {} | Subject: {} | Reset link: {}", toEmail, subject, resetLink);
        log.info("[EMAIL BODY]\n{}", body);
    }

    /**
     * Sends an email verification email with a verification link.
     */
    public void sendVerificationEmail(String toEmail, String verificationToken, String frontendUrl) {
        String verifyLink = frontendUrl + "/verify-email?token=" + verificationToken;

        String subject = "Verify Your Email Address";
        String body = buildVerificationEmail(verifyLink);

        log.info("[EMAIL] To: {} | Subject: {} | Verify link: {}", toEmail, subject, verifyLink);
        log.info("[EMAIL BODY]\n{}", body);
    }

    /**
     * Sends a welcome email after successful registration.
     */
    public void sendWelcomeEmail(String toEmail, String firstName) {
        String subject = "Welcome to RentCar SaaS";
        String body = buildWelcomeEmail(firstName);

        log.info("[EMAIL] To: {} | Subject: {}", toEmail, subject);
        log.info("[EMAIL BODY]\n{}", body);
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
        log.info("[EMAIL] To: {} | Subject: {} | URL: {}", toEmail, subject, signingUrl);
        log.info("[EMAIL BODY]\n{}", body);
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
        log.info("[EMAIL] To: {} | Subject: {}", toEmail, subject);
        log.info("[EMAIL BODY]\n{}", body);
    }

    // ── Email templates ─────────────────────────────────────────────────────

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
