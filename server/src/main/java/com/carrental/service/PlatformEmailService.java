package com.carrental.service;

import com.carrental.entity.ContactRequest;
import com.carrental.entity.Contract;
import com.carrental.entity.Deposit;
import com.carrental.entity.EmailLog;
import com.carrental.entity.PlatformSettings;
import com.carrental.entity.SupportMessage;
import com.carrental.entity.SupportTicket;
import com.carrental.entity.Tenant;
import com.carrental.repository.ContactRequestRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.repository.EmailLogRepository;
import com.carrental.repository.PlatformSettingsRepository;
import com.carrental.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Central email dispatch for business-level emails (contract signed, test, etc.).
 *
 * <p>Every method is fire-and-forget: failures are logged and written to
 * {@link EmailLog} but never propagated — business operations must not roll
 * back because an email could not be delivered.
 *
 * <p>All delivery goes through {@link SmtpMailService}, which sends every
 * message via the ZeptoMail HTTPS API ({@link HttpEmailProvider}) — SMTP is
 * never attempted. ZEPTOMAIL_API_TOKEN and EMAIL_FROM_EMAIL must be set as
 * environment variables for any delivery to succeed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformEmailService {

    private final SmtpMailService           smtpMailService;
    private final ContractRepository        contractRepository;
    private final EmailLogRepository        emailLogRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final EmailTemplateService      emailTemplateService;
    private final PdfService                pdfService;
    private final DepositRepository         depositRepository;
    private final SupportTicketRepository   supportTicketRepository;
    private final ContactRequestRepository  contactRequestRepository;
    private final com.carrental.repository.TenantSettingsRepository tenantSettingsRepository;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.public-api-url:http://localhost:8082}")
    private String publicApiUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends the "contract signed" confirmation email to the client.
     *
     * <p>Idempotent: skipped if a SENT log already exists for this contract + type.
     * Safe: if email or SMTP is missing/broken, logs the error without throwing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendContractSignedEmail(Long contractId) {
        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null) {
            log.warn("[EMAIL] sendContractSignedEmail: contract {} not found", contractId);
            return;
        }

        String toEmail = contract.getClientEmail();
        if (!StringUtils.hasText(toEmail)) {
            log.info("[EMAIL] Skipped CONTRACT_SIGNED_CLIENT — no client email [contractId={}]", contractId);
            saveLog(contractId, contract.getTenant().getId(), toEmail,
                    EmailLog.TYPE_CONTRACT_SIGNED_CLIENT, buildSubject(contract), "FAILED",
                    "EMAIL_TO_ADDRESS_MISSING", "Client email not provided");
            return;
        }

        if (alreadySent(contractId, EmailLog.TYPE_CONTRACT_SIGNED_CLIENT)) {
            log.info("[EMAIL] Skipped CONTRACT_SIGNED_CLIENT — already sent [contractId={}]", contractId);
            return;
        }

        sendContractPdfEmail(contract);
    }

    /**
     * Resends the contract email, bypassing the dedup check.
     * Intended for the Agency Admin "Resend" button. Returns the full provider
     * result (not just a boolean) so the caller can show the real failure
     * reason (invalid token, unverified sender, provider outage, timeout...)
     * instead of a generic "check your SMTP settings" message — SMTP is no
     * longer used at all.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SmtpMailService.SmtpResult resendContractEmail(Long contractId) {
        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null) {
            return SmtpMailService.SmtpResult.failure(null, "Contract not found.", "CONTRACT_NOT_FOUND");
        }

        String toEmail = contract.getClientEmail();
        if (!StringUtils.hasText(toEmail)) {
            saveLog(contractId, contract.getTenant().getId(), null,
                    EmailLog.TYPE_CONTRACT_SIGNED_CLIENT, null, "FAILED",
                    "EMAIL_TO_ADDRESS_MISSING", "Client email not provided");
            return SmtpMailService.SmtpResult.failure(null, "Client email not provided.", "EMAIL_TO_ADDRESS_MISSING");
        }

        return sendContractPdfEmail(contract);
    }

    /**
     * Builds the signed-contract email (subject, HTML/plain body, PDF attachment)
     * and dispatches it. Shared by the automatic post-signature send and the
     * manual "Send/Resend Email" button — both must attach the current PDF.
     */
    private SmtpMailService.SmtpResult sendContractPdfEmail(Contract contract) {
        Long contractId = contract.getId();
        String toEmail = contract.getClientEmail();

        Map<String, String> vars = buildContractVars(contract);
        // Try the managed template first; fall back to the inline builder if missing
        var rendered = emailTemplateService.render(
                EmailTemplateService.KEY_CONTRACT_SIGNED_CLIENT, resolveTenantEmailLanguage(contract.getTenant()), vars);

        String subject   = rendered.map(EmailTemplateService.RenderedEmail::subject)
                                   .orElseGet(() -> buildSubject(contract));
        String htmlBody  = rendered.map(EmailTemplateService.RenderedEmail::htmlBody)
                                   .filter(StringUtils::hasText).orElse(null);
        String plainBody = rendered.map(EmailTemplateService.RenderedEmail::plainBody)
                                   .filter(StringUtils::hasText)
                                   .orElseGet(() -> buildContractSignedBody(contract));
        if (htmlBody == null) {
            htmlBody = buildContractSignedHtmlBody(contract);
        }

        byte[] pdfBytes = generatePdfForEmail(contract);
        String attachmentName = sanitizeFileName(
                (StringUtils.hasText(contract.getContractNumber()) ? contract.getContractNumber() : "contrat")
                        + ".pdf");

        SmtpMailService.SmtpResult result = pdfBytes != null
                ? smtpMailService.sendForTenant(contract.getTenant().getId(), toEmail, subject, htmlBody, plainBody,
                        attachmentName, pdfBytes, "application/pdf")
                : smtpMailService.sendForTenant(contract.getTenant().getId(), toEmail, subject, htmlBody, plainBody);

        if (result.sent()) {
            saveLog(contractId, contract.getTenant().getId(), toEmail,
                    EmailLog.TYPE_CONTRACT_SIGNED_CLIENT, subject, "SENT", null, null);
            log.info("[EMAIL] CONTRACT_SIGNED_CLIENT sent [contractId={}, to={}, attached={}]",
                    contractId, toEmail, pdfBytes != null);
            return result;
        } else {
            String errorCode = classifyError(result);
            saveLog(contractId, contract.getTenant().getId(), toEmail,
                    EmailLog.TYPE_CONTRACT_SIGNED_CLIENT, subject, "FAILED",
                    errorCode, truncate(result.errorMessage(), 900));
            log.warn("[EMAIL] CONTRACT_SIGNED_CLIENT failed [contractId={}, errorCode={}]: {}",
                    contractId, errorCode, result.errorMessage());
            return result;
        }
    }

    /** Generates the current signed-contract PDF for attachment; never throws. */
    private byte[] generatePdfForEmail(Contract contract) {
        try {
            Deposit deposit = depositRepository.findByContractId(contract.getId()).orElse(null);
            return pdfService.generateContractPdf(contract, contract.getTenant(), deposit);
        } catch (Exception e) {
            log.error("[EMAIL] PDF generation failed for attachment [contractId={}]", contract.getId(), e);
            return null;
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * Sends a test email through the platform's ZeptoMail configuration.
     * Returns a user-safe result message.
     */
    public SmtpMailService.SmtpResult sendTestEmail(String toEmail) {
        String subject = "Innovacar Email Test";
        String plainBody =
                "This is a test email sent from the Innovacar / RentCar SaaS platform email configuration (ZeptoMail).\n\n" +
                "If you received this email, your email settings are working correctly.\n\n" +
                "— RentCar / Innovax Technologies";
        String htmlBody = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 0;">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                    <tr><td style="background:#1a56db;padding:32px 40px;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">RentCar</h1>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">Email Test Successful</h2>
                      <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                        This is a test email sent from the Innovacar / RentCar SaaS platform email configuration (ZeptoMail).
                      </p>
                      <p style="margin:0;color:#6b7280;font-size:14px;line-height:1.6;">
                        If you received this email, your email settings are working correctly.
                      </p>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;">
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        &copy; 2025 RentCar SaaS &mdash; Innovax Technologies. All rights reserved.
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """;

        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, htmlBody, plainBody);

        emailLogRepository.save(EmailLog.builder()
                .recipient(toEmail)
                .subject(subject)
                .emailType(EmailLog.TYPE_SMTP_TEST)
                .status(result.sent() ? "SENT" : "FAILED")
                .errorCode(result.sent() ? null : classifyError(result))
                .errorMessage(result.sent() ? null : truncate(result.errorMessage(), 900))
                .templateName("Platform email test")
                .provider(smtpMailService.activeProvider())
                .build());

        // Record test result in PlatformSettings
        platformSettingsRepository.findTopByOrderByIdAsc().ifPresent(ps -> {
            ps.setLastSmtpTestStatus(result.sent() ? "SENT" : "FAILED");
            ps.setLastSmtpTestAt(java.time.LocalDateTime.now());
            platformSettingsRepository.save(ps);
        });

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean alreadySent(Long contractId, String emailType) {
        return emailLogRepository.existsByContractIdAndEmailTypeAndStatus(contractId, emailType, "SENT");
    }

    private void saveLog(Long contractId, Long tenantId, String recipient,
                         String emailType, String subject, String status,
                         String errorCode, String errorMessage) {
        emailLogRepository.save(EmailLog.builder()
                .contractId(contractId)
                .tenantId(tenantId)
                .recipient(recipient != null ? recipient : "")
                .emailType(emailType)
                .subject(subject)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .templateName(emailType)
                .provider(smtpMailService.activeProvider())
                .build());
    }

    /** Builds the variable map used by the CONTRACT_SIGNED_CLIENT template. */
    private Map<String, String> buildContractVars(Contract contract) {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("clientName",     StringUtils.hasText(contract.getClientFullName()) ? contract.getClientFullName() : "Valued Client");
        vars.put("contractNumber", contract.getContractNumber());
        vars.put("vehicleName",    buildVehicleLine(contract));
        vars.put("plateNumber",    StringUtils.hasText(contract.getVehicleRegistration()) ? contract.getVehicleRegistration() : "");
        vars.put("startDate",      format(contract.getStartDate()));
        vars.put("endDate",        format(contract.getEndDate()));
        vars.put("totalAmount",    contract.getTotalPrice() != null ? contract.getTotalPrice().toPlainString() + " MAD" : "N/A");
        vars.put("rentalTotal",    rentalTotalText(contract));
        vars.put("depositAmount",  depositAmountText(contract));
        vars.put("startTime",      contract.getPickupTime() != null ? contract.getPickupTime().toString() : "");
        vars.put("endTime",        contract.getReturnTime() != null ? contract.getReturnTime().toString() : "");
        vars.put("agencyName",     contract.getTenant() != null ? contract.getTenant().getName() : "");
        String pdfUrl = buildPdfDownloadUrl(contract);
        vars.put("contractPdfUrl", isValidPublicUrl(pdfUrl) ? pdfUrl : "");
        vars.put("contractPdfSection", buildPdfSection(contract));
        vars.put("contractPdfPlainSection", buildPdfPlainSection(contract));
        return vars;
    }

    private String buildSubject(Contract contract) {
        return "Votre contrat de location " + contract.getContractNumber() + " est signé";
    }

    /** Rental total (deposit is never included — it is not rental revenue). */
    private String rentalTotalText(Contract contract) {
        return contract.getTotalPrice() != null
                ? contract.getTotalPrice().stripTrailingZeros().toPlainString() + " MAD"
                : "N/A";
    }

    /** Deposit/caution text, falling back to the linked Deposit record and to "Non exigée" when zero. */
    private String depositAmountText(Contract contract) {
        BigDecimal deposit = contract.getDepositAmount();
        if (deposit == null) {
            Deposit d = depositRepository.findByContractId(contract.getId()).orElse(null);
            deposit = d != null ? d.getAmount() : null;
        }
        if (deposit == null || deposit.compareTo(BigDecimal.ZERO) == 0) {
            return "Non exigée";
        }
        return deposit.stripTrailingZeros().toPlainString() + " MAD";
    }

    private String buildContractSignedBody(Contract contract) {
        String clientName = StringUtils.hasText(contract.getClientFullName())
                ? contract.getClientFullName() : "Client";
        String vehicle     = buildVehicleLine(contract);
        String startDate   = format(contract.getStartDate());
        String endDate     = format(contract.getEndDate());
        String startTime   = contract.getPickupTime() != null ? contract.getPickupTime().toString() : "";
        String endTime     = contract.getReturnTime() != null ? contract.getReturnTime().toString() : "";
        String agencyName  = contract.getTenant() != null ? contract.getTenant().getName() : "";

        return String.format("""
                Bonjour %s,

                Votre contrat de location %s a été signé avec succès.

                Vous trouverez le contrat PDF en pièce jointe.

                Détails:
                - Véhicule: %s
                - Départ: %s %s
                - Retour: %s %s
                - Montant location: %s
                - Caution: %s

                Merci pour votre confiance,
                %s
                """,
                clientName,
                contract.getContractNumber(),
                vehicle,
                startDate, startTime,
                endDate, endTime,
                rentalTotalText(contract),
                depositAmountText(contract),
                agencyName);
    }

    /** Designed HTML version of the signed-contract email (used when no custom template overrides it). */
    private String buildContractSignedHtmlBody(Contract contract) {
        String clientName = escapeHtml(StringUtils.hasText(contract.getClientFullName())
                ? contract.getClientFullName() : "Client");
        String vehicle     = escapeHtml(buildVehicleLine(contract));
        String startDate   = format(contract.getStartDate());
        String endDate     = format(contract.getEndDate());
        String startTime   = contract.getPickupTime() != null ? contract.getPickupTime().toString() : "";
        String endTime     = contract.getReturnTime() != null ? contract.getReturnTime().toString() : "";
        String agencyName  = escapeHtml(contract.getTenant() != null ? contract.getTenant().getName() : "");

        return String.format("""
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1e293b;">
                  <div style="background:#0f172a;padding:20px 24px;border-radius:8px 8px 0 0;">
                    <h1 style="color:#ffffff;font-size:18px;margin:0;">%s</h1>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:24px;border-radius:0 0 8px 8px;">
                    <p>Bonjour <strong>%s</strong>,</p>
                    <p>Votre contrat de location <strong>%s</strong> a été signé avec succès.</p>
                    <p>Vous trouverez le contrat PDF en pièce jointe.</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;">
                      <tr><td style="padding:6px 0;color:#64748b;">Véhicule</td><td style="padding:6px 0;text-align:right;font-weight:600;">%s</td></tr>
                      <tr><td style="padding:6px 0;color:#64748b;">Départ</td><td style="padding:6px 0;text-align:right;font-weight:600;">%s %s</td></tr>
                      <tr><td style="padding:6px 0;color:#64748b;">Retour</td><td style="padding:6px 0;text-align:right;font-weight:600;">%s %s</td></tr>
                      <tr><td style="padding:6px 0;color:#64748b;">Montant location</td><td style="padding:6px 0;text-align:right;font-weight:600;">%s</td></tr>
                      <tr><td style="padding:6px 0;color:#64748b;">Caution</td><td style="padding:6px 0;text-align:right;font-weight:600;">%s</td></tr>
                    </table>
                    <p>Merci pour votre confiance,<br/><strong>%s</strong></p>
                    <hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0;"/>
                    <p style="font-size:11px;color:#94a3b8;">Cet email a été envoyé automatiquement. Merci de ne pas y répondre.</p>
                  </div>
                </div>
                """,
                agencyName,
                clientName,
                contract.getContractNumber(),
                vehicle,
                startDate, startTime,
                endDate, endTime,
                rentalTotalText(contract),
                depositAmountText(contract),
                agencyName);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Builds the public signed-PDF download URL, or {@code null} if no secure
     * token exists yet. NEVER returns a placeholder/fallback string here — a
     * non-URL value returned from this method used to get written straight
     * into an {@code <a href>}, which is exactly what produced the broken
     * "http://(PDF link not available — contact your agency)" button (browsers
     * silently prepend a scheme to whatever a href contains, which is also why
     * Gmail flagged it as a suspicious redirect). Callers must render a real
     * button only when this returns a value that also passes {@link #isValidPublicUrl}.
     */
    String buildPdfDownloadUrl(Contract contract) {
        if (contract.getQrToken() == null) {
            return null;
        }
        String base = publicApiUrl.replaceAll("/+$", "");
        return base + "/api/public/contracts/" + contract.getId() + "/" + contract.getQrToken() + "/pdf";
    }

    /**
     * Strict allow-list for URLs that are safe to embed in an outbound email.
     * Accepts a real https origin (production), or http against localhost/127.0.0.1
     * (local dev only) — everything else is rejected, including blank/null values,
     * placeholder text, values with spaces, 192.168.x.x, and *.railway.internal.
     */
    boolean isValidPublicUrl(String value) {
        if (value == null || value.isBlank()) return false;
        String trimmed = value.trim();
        if (trimmed.contains(" ") || trimmed.startsWith("(")) return false;
        try {
            java.net.URI uri = java.net.URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return false;
            String lowerHost = host.toLowerCase(java.util.Locale.ROOT);
            if (lowerHost.contains("railway.internal")) return false;
            boolean isLoopback = lowerHost.equals("localhost") || lowerHost.startsWith("127.") || lowerHost.startsWith("192.168.");
            if ("https".equalsIgnoreCase(scheme)) return !isLoopback;
            if ("http".equalsIgnoreCase(scheme)) return isLoopback;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static final String PDF_LINK_UNAVAILABLE_TEXT =
            "The signed PDF is still being generated. Please contact your rental agency.";

    /**
     * HTML fragment for the CONTRACT_SIGNED_CLIENT template's {{contractPdfSection}}
     * placeholder — a real download button when (and only when) a valid public URL
     * exists, otherwise a plain non-clickable message. Never a dead/placeholder link.
     */
    String buildPdfSection(Contract contract) {
        String url = buildPdfDownloadUrl(contract);
        if (!isValidPublicUrl(url)) {
            if (url != null) {
                log.warn("[EMAIL] Contract [id={}] PDF URL failed validation (host/scheme not allowed) — omitting download button", contract.getId());
            }
            return "<p style=\"margin:20px 0 0;font-size:14px;line-height:1.6;color:#64748b;\">"
                 + PDF_LINK_UNAVAILABLE_TEXT + "</p>";
        }
        return "<div style=\"text-align:center;margin:28px 0;\">"
             + "<a href=\"" + url + "\" target=\"_blank\" rel=\"noopener noreferrer\" style=\""
             + "display:inline-block;padding:14px 32px;background:linear-gradient(135deg,#0f766e,#10b981);"
             + "color:#ffffff;text-decoration:none;border-radius:12px;font-weight:700;font-size:15px;"
             + "letter-spacing:0.02em;box-shadow:0 4px 14px rgba(15,118,110,0.35);\">"
             + "Download Contract PDF</a></div>";
    }

    /** Plain-text equivalent of {@link #buildPdfSection} for the {{contractPdfPlainSection}} placeholder. */
    String buildPdfPlainSection(Contract contract) {
        String url = buildPdfDownloadUrl(contract);
        if (!isValidPublicUrl(url)) {
            return PDF_LINK_UNAVAILABLE_TEXT;
        }
        return "Download your contract PDF:\n" + url;
    }

    private String buildVehicleLine(Contract contract) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(contract.getVehicleBrand()))       sb.append(contract.getVehicleBrand()).append(" ");
        if (StringUtils.hasText(contract.getVehicleModel()))       sb.append(contract.getVehicleModel());
        if (StringUtils.hasText(contract.getVehicleRegistration())) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(contract.getVehicleRegistration());
        }
        return sb.length() > 0 ? sb.toString().trim() : "N/A";
    }

    private String format(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "N/A";
    }

    /** Prefers the typed errorCode HttpEmailProvider already produced over re-parsing the message text. */
    private String classifyError(SmtpMailService.SmtpResult result) {
        if (result.errorCode() != null) return result.errorCode();
        String errorMessage = result.errorMessage();
        if (errorMessage == null) return "EMAIL_SEND_FAILED";
        String lower = errorMessage.toLowerCase();
        if (lower.contains("authentication") || lower.contains("535") || lower.contains("credentials"))
            return "EMAIL_API_AUTH_FAILED";
        if (lower.contains("timeout") || lower.contains("timed out"))
            return "EMAIL_API_TIMEOUT";
        if (lower.contains("unreachable") || lower.contains("connect") || lower.contains("refused"))
            return "EMAIL_API_PROVIDER_ERROR";
        if (lower.contains("not configured") || lower.contains("unconfigured") || lower.contains("not set"))
            return "EMAIL_CONFIGURATION_MISSING";
        return "EMAIL_SEND_FAILED";
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * Resolves the language to render a client/requester-facing email in: the agency's
     * default language (Settings → Operations → Language), falling back to EN. There is
     * no per-client or per-anonymous-requester language stored anywhere in this schema —
     * {@code Client} and {@code ContactRequest} have no language field — so the tenant
     * default is the best signal actually available, matching the documented resolution
     * order (recipient → tenant → platform default) for the "recipient" step we can't
     * populate today. {@code EmailTemplateService} stores/expects uppercase language
     * codes ("EN"/"FR"/"AR"); {@code TenantSettings.language} stores lowercase, hence the
     * uppercase conversion here.
     */
    private String resolveTenantEmailLanguage(Tenant tenant) {
        if (tenant == null) return "EN";
        return tenantSettingsRepository.findByTenantId(tenant.getId())
                .map(com.carrental.entity.TenantSettings::getLanguage)
                .filter(StringUtils::hasText)
                .map(lang -> lang.toUpperCase(java.util.Locale.ROOT))
                .orElse("EN");
    }

    // ── Subscription lifecycle emails ─────────────────────────────────────────

    /**
     * Notifies the agency that their cancellation has been scheduled.
     * Sent immediately when the agency requests cancellation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSubscriptionCancellationScheduled(Long tenantId, String tenantEmail, String tenantName, LocalDateTime cancelEffectiveAt) {
        if (!StringUtils.hasText(tenantEmail)) {
            log.warn("[EMAIL] sendSubscriptionCancellationScheduled: tenantId={} has no email", tenantId);
            return;
        }
        String subject = "Your subscription cancellation is scheduled — " + tenantName;
        String effectiveDateStr = cancelEffectiveAt != null
                ? cancelEffectiveAt.toLocalDate().format(DATE_FMT)
                : "end of current period";
        String body = String.format("""
                Hello %s,

                Your subscription cancellation has been scheduled.

                ─────────────────────────────
                Your access remains fully active until: %s
                After that date, your account will switch to read-only mode.
                ─────────────────────────────

                Changed your mind? You can undo this cancellation anytime before %s
                by visiting Subscription & Billing in your dashboard.

                Thank you for being a RentCar customer.

                ─────────────────────────────
                This email was sent automatically by RentCar / Innovax Technologies.
                """, tenantName, effectiveDateStr, effectiveDateStr);

        SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(tenantId, tenantEmail, subject, body);
        String status = result.sent() ? "SENT" : "FAILED";
        String errorCode = result.sent() ? null : classifyError(result);
        saveLog(null, tenantId, tenantEmail, EmailLog.TYPE_SUBSCRIPTION_CANCEL_SCHEDULED,
                subject, status, errorCode, truncate(result.errorMessage(), 900));
        log.info("[EMAIL] {} tenantId={} to={} status={}", EmailLog.TYPE_SUBSCRIPTION_CANCEL_SCHEDULED, tenantId, tenantEmail, status);
    }

    /**
     * Notifies the agency that their pending cancellation has been undone.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSubscriptionCancellationUndone(Long tenantId, String tenantEmail, String tenantName) {
        if (!StringUtils.hasText(tenantEmail)) {
            log.warn("[EMAIL] sendSubscriptionCancellationUndone: tenantId={} has no email", tenantId);
            return;
        }
        String subject = "Subscription cancellation reversed — " + tenantName;
        String body = String.format("""
                Hello %s,

                Great news — your subscription cancellation has been reversed.

                Your subscription is now active again and will renew as scheduled.
                No further action is needed.

                ─────────────────────────────
                This email was sent automatically by RentCar / Innovax Technologies.
                """, tenantName);

        SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(tenantId, tenantEmail, subject, body);
        String status = result.sent() ? "SENT" : "FAILED";
        String errorCode = result.sent() ? null : classifyError(result);
        saveLog(null, tenantId, tenantEmail, EmailLog.TYPE_SUBSCRIPTION_CANCEL_UNDONE,
                subject, status, errorCode, truncate(result.errorMessage(), 900));
        log.info("[EMAIL] {} tenantId={} to={} status={}", EmailLog.TYPE_SUBSCRIPTION_CANCEL_UNDONE, tenantId, tenantEmail, status);
    }

    /**
     * Notifies the agency that their subscription has been permanently cancelled
     * after the end-of-period lifecycle job ran.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSubscriptionCancelledFinal(Long tenantId, String tenantEmail, String tenantName) {
        if (!StringUtils.hasText(tenantEmail)) {
            log.warn("[EMAIL] sendSubscriptionCancelledFinal: tenantId={} has no email", tenantId);
            return;
        }
        String subject = "Your subscription has ended — " + tenantName;
        String body = String.format("""
                Hello %s,

                Your RentCar subscription has now ended.

                Your account data is preserved and you can still log in to view your existing records.
                To reactivate your subscription and regain full access, visit Subscription & Billing
                in your dashboard and choose a plan.

                ─────────────────────────────
                This email was sent automatically by RentCar / Innovax Technologies.
                """, tenantName);

        SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(tenantId, tenantEmail, subject, body);
        String status = result.sent() ? "SENT" : "FAILED";
        String errorCode = result.sent() ? null : classifyError(result);
        saveLog(null, tenantId, tenantEmail, EmailLog.TYPE_SUBSCRIPTION_CANCELLED_FINAL,
                subject, status, errorCode, truncate(result.errorMessage(), 900));
        log.info("[EMAIL] {} tenantId={} to={} status={}", EmailLog.TYPE_SUBSCRIPTION_CANCELLED_FINAL, tenantId, tenantEmail, status);
    }

    /**
     * Trial-expiry-approaching reminder (7/3/1 day(s) before end). Dedup is the
     * caller's responsibility (see TrialExpiryJob, which stamps a per-milestone
     * "already sent" timestamp on the tenant before/after calling this) — this
     * method always sends unconditionally when invoked.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendTrialReminder(Long tenantId, String tenantEmail, String tenantName, long daysRemaining) {
        if (!StringUtils.hasText(tenantEmail)) {
            log.warn("[EMAIL] sendTrialReminder: tenantId={} has no email", tenantId);
            return;
        }
        String dayWord = daysRemaining == 1 ? "day" : "days";
        String subject = "Your Innovacar trial ends in " + daysRemaining + " " + dayWord;
        String body = String.format("""
                Hello %s,

                Your Innovacar free trial ends in %d %s.

                ─────────────────────────────
                To keep full access to your agency's reservations, contracts, vehicles,
                and reports without interruption, choose a plan before your trial ends.
                ─────────────────────────────

                Visit Subscription & Billing in your dashboard to upgrade anytime.

                ─────────────────────────────
                This email was sent automatically by Innovacar / Innovax Technologies.
                """, tenantName, daysRemaining, dayWord);

        SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(tenantId, tenantEmail, subject, body);
        String status = result.sent() ? "SENT" : "FAILED";
        String errorCode = result.sent() ? null : classifyError(result);
        saveLog(null, tenantId, tenantEmail, EmailLog.TYPE_TRIAL_REMINDER,
                subject, status, errorCode, truncate(result.errorMessage(), 900));
        log.info("[EMAIL] {} tenantId={} to={} daysRemaining={} status={}",
                EmailLog.TYPE_TRIAL_REMINDER, tenantId, tenantEmail, daysRemaining, status);
    }

    /**
     * Sent once, the day the trial actually expires with no paid subscription active.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendTrialExpired(Long tenantId, String tenantEmail, String tenantName) {
        if (!StringUtils.hasText(tenantEmail)) {
            log.warn("[EMAIL] sendTrialExpired: tenantId={} has no email", tenantId);
            return;
        }
        String subject = "Your Innovacar free trial has ended — " + tenantName;
        String body = String.format("""
                Hello %s,

                Your Innovacar free trial has ended.

                Your account data is preserved and you can still log in, but premium
                business operations (reservations, contracts, invoices, GPS) are now
                paused until you choose a plan.

                Visit Subscription & Billing in your dashboard to reactivate your account.

                ─────────────────────────────
                This email was sent automatically by Innovacar / Innovax Technologies.
                """, tenantName);

        SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(tenantId, tenantEmail, subject, body);
        String status = result.sent() ? "SENT" : "FAILED";
        String errorCode = result.sent() ? null : classifyError(result);
        saveLog(null, tenantId, tenantEmail, EmailLog.TYPE_TRIAL_EXPIRED,
                subject, status, errorCode, truncate(result.errorMessage(), 900));
        log.info("[EMAIL] {} tenantId={} to={} status={}", EmailLog.TYPE_TRIAL_EXPIRED, tenantId, tenantEmail, status);
    }

    // ── Support Center emails ─────────────────────────────────────────────────

    /**
     * Notifies the internal team (destinationEmail resolved by SupportRoutingService)
     * that a new ticket was created. Runs in its own transaction so a send failure
     * never rolls back the ticket creation that triggered it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSupportTicketCreatedInternal(SupportTicket ticket) {
        if (!StringUtils.hasText(ticket.getDestinationEmail())) {
            log.warn("[EMAIL] sendSupportTicketCreatedInternal: ticket {} has no destinationEmail", ticket.getTicketNumber());
            markEmailStatus(ticket, "FAILED");
            return;
        }

        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("ticketNumber", ticket.getTicketNumber());
        vars.put("channel", StringUtils.hasText(ticket.getChannel()) ? ticket.getChannel() : "");
        vars.put("category", ticket.getCategory() != null ? ticket.getCategory().name() : "");
        vars.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "");
        vars.put("agencyName", ticket.getTenant() != null && StringUtils.hasText(ticket.getTenant().getName())
                ? ticket.getTenant().getName() : "Anonymous / Public");
        vars.put("requesterName", StringUtils.hasText(ticket.getRequesterName()) ? ticket.getRequesterName() : "Unknown");
        vars.put("requesterEmail", StringUtils.hasText(ticket.getRequesterEmail()) ? ticket.getRequesterEmail() : "");
        vars.put("subject", StringUtils.hasText(ticket.getSubject()) ? ticket.getSubject() : "");
        vars.put("message", StringUtils.hasText(ticket.getDescription()) ? ticket.getDescription() : "");
        vars.put("dashboardUrl", frontendUrl + "/super-admin/support/" + ticket.getId());

        // Internal notification to the platform support team (destinationEmail), not the
        // ticket requester — platform staff language isn't tracked anywhere, so EN is the
        // correct, deliberate choice here, not a hardcoded oversight.
        var rendered = emailTemplateService.render(
                EmailTemplateService.KEY_SUPPORT_TICKET_CREATED_INTERNAL, "EN", vars);
        String subject = rendered.map(EmailTemplateService.RenderedEmail::subject)
                .orElse("[" + ticket.getPriority() + "] " + ticket.getTicketNumber() + " — " + ticket.getSubject());
        String htmlBody = rendered.map(EmailTemplateService.RenderedEmail::htmlBody).filter(StringUtils::hasText).orElse(null);
        String plainBody = rendered.map(EmailTemplateService.RenderedEmail::plainBody).filter(StringUtils::hasText)
                .orElse(ticket.getSubject() + "\n\n" + ticket.getDescription());

        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(
                ticket.getDestinationEmail(), subject, htmlBody, plainBody);

        String status = result.sent() ? "SENT" : "FAILED";
        saveTicketLog(ticket.getId(), ticket.getTenant() != null ? ticket.getTenant().getId() : null,
                ticket.getDestinationEmail(), EmailLog.TYPE_SUPPORT_TICKET_CREATED, subject, status,
                result.sent() ? null : classifyError(result), truncate(result.errorMessage(), 900));
        markEmailStatus(ticket, status);
        log.info("[EMAIL] SUPPORT_TICKET_CREATED ticket={} to={} status={}", ticket.getTicketNumber(), ticket.getDestinationEmail(), status);
    }

    /**
     * Sends the "we received your request" confirmation to the requester, if a
     * requester email is available (public/authenticated submitters alike).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSupportTicketConfirmation(SupportTicket ticket) {
        String toEmail = StringUtils.hasText(ticket.getRequesterEmail()) ? ticket.getRequesterEmail() : ticket.getContactEmail();
        if (!StringUtils.hasText(toEmail)) {
            return;
        }

        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("userName", StringUtils.hasText(ticket.getRequesterName()) ? ticket.getRequesterName() : "there");
        vars.put("ticketNumber", ticket.getTicketNumber());
        vars.put("supportUrl", frontendUrl + "/tickets/" + ticket.getId());

        String templateKey = SupportRoutingService.CHANNEL_CONTACT.equals(ticket.getChannel())
                ? EmailTemplateService.KEY_CONTACT_FORM_RECEIVED
                : EmailTemplateService.KEY_SUPPORT_TICKET_CREATED;
        var rendered = emailTemplateService.render(templateKey, resolveTenantEmailLanguage(ticket.getTenant()), vars);
        String subject = rendered.map(EmailTemplateService.RenderedEmail::subject)
                .orElse("We received your request — " + ticket.getTicketNumber());
        String htmlBody = rendered.map(EmailTemplateService.RenderedEmail::htmlBody).filter(StringUtils::hasText).orElse(null);
        String plainBody = rendered.map(EmailTemplateService.RenderedEmail::plainBody).filter(StringUtils::hasText)
                .orElse("We received your request. Ticket number: " + ticket.getTicketNumber());

        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, htmlBody, plainBody);
        saveTicketLog(ticket.getId(), ticket.getTenant() != null ? ticket.getTenant().getId() : null,
                toEmail, EmailLog.TYPE_CONTACT_FORM, subject, result.sent() ? "SENT" : "FAILED",
                result.sent() ? null : classifyError(result), truncate(result.errorMessage(), 900));
        log.info("[EMAIL] SUPPORT_TICKET_CONFIRMATION ticket={} to={} status={}",
                ticket.getTicketNumber(), toEmail, result.sent() ? "SENT" : "FAILED");
    }

    // ── Contact Us emails (ContactRequest — separate from Support Tickets) ─────

    /**
     * Notifies the internal team (destinationEmail resolved by SupportRoutingService)
     * that a new contact request was submitted. Runs in its own transaction so a send
     * failure never rolls back the contact request creation that triggered it. Callers
     * should still wrap this call in a try/catch, since an exception thrown here
     * (as opposed to a merely-failed send) will still propagate to the caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendContactRequestCreatedInternal(ContactRequest request) {
        if (!StringUtils.hasText(request.getDestinationEmail())) {
            log.warn("[EMAIL] sendContactRequestCreatedInternal: request {} has no destinationEmail", request.getRequestNumber());
            markContactEmailStatus(request, "FAILED");
            return;
        }

        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("ticketNumber", request.getRequestNumber());
        vars.put("channel", SupportRoutingService.CHANNEL_CONTACT);
        vars.put("category", StringUtils.hasText(request.getCategory()) ? request.getCategory() : "");
        vars.put("priority", "");
        vars.put("agencyName", "Anonymous / Public");
        vars.put("requesterName", StringUtils.hasText(request.getRequesterName()) ? request.getRequesterName() : "Unknown");
        vars.put("requesterEmail", StringUtils.hasText(request.getRequesterEmail()) ? request.getRequesterEmail() : "");
        vars.put("subject", StringUtils.hasText(request.getSubject()) ? request.getSubject() : "");
        vars.put("message", StringUtils.hasText(request.getMessage()) ? request.getMessage() : "");
        vars.put("dashboardUrl", frontendUrl + "/super-admin/contact-requests/" + request.getId());

        // Internal notification to the platform support team, not the contact-form
        // submitter — same reasoning as sendSupportTicketCreatedInternal above.
        var rendered = emailTemplateService.render(
                EmailTemplateService.KEY_SUPPORT_TICKET_CREATED_INTERNAL, "EN", vars);
        String subject = rendered.map(EmailTemplateService.RenderedEmail::subject)
                .orElse("[Contact] " + request.getRequestNumber() + " — " + request.getSubject());
        String htmlBody = rendered.map(EmailTemplateService.RenderedEmail::htmlBody).filter(StringUtils::hasText).orElse(null);
        String plainBody = rendered.map(EmailTemplateService.RenderedEmail::plainBody).filter(StringUtils::hasText)
                .orElse(request.getSubject() + "\n\n" + request.getMessage());

        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(
                request.getDestinationEmail(), subject, htmlBody, plainBody);

        String status = result.sent() ? "SENT" : "FAILED";
        saveTicketLog(request.getId(), null, request.getDestinationEmail(),
                EmailLog.TYPE_CONTACT_REQUEST_CREATED, subject, status,
                result.sent() ? null : classifyError(result), truncate(result.errorMessage(), 900));
        markContactEmailStatus(request, status);
        log.info("[EMAIL] CONTACT_REQUEST_CREATED request={} to={} status={}", request.getRequestNumber(), request.getDestinationEmail(), status);
    }

    /** Sends the "we received your request" confirmation to the contact-form submitter. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendContactRequestConfirmation(ContactRequest request) {
        String toEmail = request.getRequesterEmail();
        if (!StringUtils.hasText(toEmail)) {
            return;
        }

        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("userName", StringUtils.hasText(request.getRequesterName()) ? request.getRequesterName() : "there");
        vars.put("ticketNumber", request.getRequestNumber());
        vars.put("supportUrl", frontendUrl + "/contact");

        // ContactRequest is always anonymous/public (no tenant column at all, by design —
        // see ContactRequest's own class javadoc), so there is no language signal available
        // for this recipient; EN is the genuine platform default, not an oversight.
        var rendered = emailTemplateService.render(EmailTemplateService.KEY_CONTACT_FORM_RECEIVED, "EN", vars);
        String subject = rendered.map(EmailTemplateService.RenderedEmail::subject)
                .orElse("We received your request — " + request.getRequestNumber());
        String htmlBody = rendered.map(EmailTemplateService.RenderedEmail::htmlBody).filter(StringUtils::hasText).orElse(null);
        String plainBody = rendered.map(EmailTemplateService.RenderedEmail::plainBody).filter(StringUtils::hasText)
                .orElse("We received your request. Reference number: " + request.getRequestNumber());

        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, htmlBody, plainBody);
        saveTicketLog(request.getId(), null, toEmail,
                EmailLog.TYPE_CONTACT_FORM, subject, result.sent() ? "SENT" : "FAILED",
                result.sent() ? null : classifyError(result), truncate(result.errorMessage(), 900));
        log.info("[EMAIL] CONTACT_REQUEST_CONFIRMATION request={} to={} status={}",
                request.getRequestNumber(), toEmail, result.sent() ? "SENT" : "FAILED");
    }

    private void markContactEmailStatus(ContactRequest request, String status) {
        request.setEmailStatus(status);
        contactRequestRepository.save(request);
    }

    /** Notifies the requester that support replied to their ticket. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSupportReplyNotification(SupportTicket ticket, SupportMessage message) {
        String toEmail = StringUtils.hasText(ticket.getRequesterEmail()) ? ticket.getRequesterEmail() : ticket.getContactEmail();
        if (!StringUtils.hasText(toEmail)) {
            return;
        }

        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("userName", StringUtils.hasText(ticket.getRequesterName()) ? ticket.getRequesterName() : "there");
        vars.put("ticketNumber", ticket.getTicketNumber());
        vars.put("replyMessage", StringUtils.hasText(message.getMessage()) ? message.getMessage() : "");
        vars.put("supportUrl", frontendUrl + "/tickets/" + ticket.getId());

        var rendered = emailTemplateService.render(EmailTemplateService.KEY_SUPPORT_REPLY, resolveTenantEmailLanguage(ticket.getTenant()), vars);
        String subject = rendered.map(EmailTemplateService.RenderedEmail::subject)
                .orElse("New reply from RentCar Support — " + ticket.getTicketNumber());
        String htmlBody = rendered.map(EmailTemplateService.RenderedEmail::htmlBody).filter(StringUtils::hasText).orElse(null);
        String plainBody = rendered.map(EmailTemplateService.RenderedEmail::plainBody).filter(StringUtils::hasText)
                .orElse(message.getMessage());

        SmtpMailService.SmtpResult result = smtpMailService.sendPlatform(toEmail, subject, htmlBody, plainBody);
        saveTicketLog(ticket.getId(), ticket.getTenant() != null ? ticket.getTenant().getId() : null,
                toEmail, EmailLog.TYPE_SUPPORT_REPLY, subject, result.sent() ? "SENT" : "FAILED",
                result.sent() ? null : classifyError(result), truncate(result.errorMessage(), 900));
        log.info("[EMAIL] SUPPORT_REPLY ticket={} to={} status={}", ticket.getTicketNumber(), toEmail, result.sent() ? "SENT" : "FAILED");
    }

    private void markEmailStatus(SupportTicket ticket, String status) {
        ticket.setEmailStatus(status);
        supportTicketRepository.save(ticket);
    }

    private void saveTicketLog(Long ticketId, Long tenantId, String recipient,
                               String emailType, String subject, String status,
                               String errorCode, String errorMessage) {
        emailLogRepository.save(EmailLog.builder()
                .ticketId(ticketId)
                .tenantId(tenantId)
                .recipient(recipient != null ? recipient : "")
                .emailType(emailType)
                .subject(subject)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .templateName(emailType)
                .provider(smtpMailService.activeProvider())
                .build());
    }
}
