package com.carrental.service;

import com.carrental.entity.EmailTemplate;
import com.carrental.repository.EmailTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.*;

/**
 * Manages platform email templates: seeding defaults, rendering variables,
 * and providing CRUD helpers used by SuperAdminController.
 *
 * <p>Variable syntax: {{variableName}} — replaced at render time.
 * Missing variables produce an empty string and are logged at debug level.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository templateRepository;

    // ── Public record returned after rendering ────────────────────────────────

    public record RenderedEmail(String subject, String htmlBody, String plainBody) {}

    // ── Template type / category constants ───────────────────────────────────

    public static final String CAT_AUTH        = "AUTH";
    public static final String CAT_CONTRACT    = "CONTRACT";
    public static final String CAT_RESERVATION = "RESERVATION";
    public static final String CAT_PAYMENT     = "PAYMENT";
    public static final String CAT_SUPPORT     = "SUPPORT";
    public static final String CAT_GPS         = "GPS";
    public static final String CAT_SYSTEM      = "SYSTEM";

    // ── Template key constants (used by callers to request a template) ────────

    public static final String KEY_WELCOME_AGENCY            = "WELCOME_AGENCY";
    public static final String KEY_WELCOME_EMPLOYEE          = "WELCOME_EMPLOYEE";
    public static final String KEY_RESET_PASSWORD            = "RESET_PASSWORD";
    public static final String KEY_NEW_DEVICE_LOGIN          = "NEW_DEVICE_LOGIN";
    public static final String KEY_CONTRACT_SIGNED_CLIENT    = "CONTRACT_SIGNED_CLIENT";
    public static final String KEY_RESERVATION_CONFIRMED     = "RESERVATION_CONFIRMED";
    public static final String KEY_PAYMENT_RECEIVED          = "PAYMENT_RECEIVED";
    public static final String KEY_SUBSCRIPTION_TRIAL_ENDING = "SUBSCRIPTION_TRIAL_ENDING";
    public static final String KEY_SUBSCRIPTION_PAYMENT_FAILED = "SUBSCRIPTION_PAYMENT_FAILED";
    public static final String KEY_SUPPORT_TICKET_CREATED    = "SUPPORT_TICKET_CREATED";
    public static final String KEY_SUPPORT_TICKET_CREATED_INTERNAL = "SUPPORT_TICKET_CREATED_INTERNAL";
    public static final String KEY_SUPPORT_REPLY             = "SUPPORT_REPLY";
    public static final String KEY_CONTACT_FORM_RECEIVED     = "CONTACT_FORM_RECEIVED";
    public static final String KEY_GPS_GEOFENCE_ALERT        = "GPS_GEOFENCE_ALERT";
    public static final String KEY_GPS_MOVEMENT_ALERT        = "GPS_MOVEMENT_ALERT";

    // ── Startup seeding ───────────────────────────────────────────────────────

    @PostConstruct
    @Transactional
    public void seedDefaultTemplates() {
        long startNanos = System.nanoTime();
        log.info("[STARTUP_STEP_BEGIN] EmailTemplateService.seedDefaultTemplates");
        try {
            if (templateRepository.countBySystemDefaultTrue() > 0) {
                log.debug("[EMAIL_TEMPLATES] Default templates already seeded — skipping.");
                repairBrokenContractPdfButton();
                log.info("[STARTUP_STEP_OK] EmailTemplateService.seedDefaultTemplates durationMs={}",
                        (System.nanoTime() - startNanos) / 1_000_000);
                return;
            }
            List<EmailTemplate> defaults = buildAllDefaults();
            templateRepository.saveAll(defaults);
            log.info("[EMAIL_TEMPLATES] Seeded {} default email templates.", defaults.size());
            log.info("[STARTUP_STEP_OK] EmailTemplateService.seedDefaultTemplates durationMs={}",
                    (System.nanoTime() - startNanos) / 1_000_000);
        } catch (Exception e) {
            log.error("[STARTUP_STEP_FAILED] EmailTemplateService.seedDefaultTemplates exceptionClass={}",
                    e.getClass().getName());
            throw e;
        }
    }

    /**
     * One-time repair for CONTRACT_SIGNED_CLIENT rows shipped before the PDF-download-button
     * fix: the old body_html hardcoded {@code <a href="{{contractPdfUrl}}">}, which put a plain
     * fallback sentence straight into an href whenever no PDF token existed yet — browsers then
     * silently prepend a scheme to that text, producing the broken
     * "http://(PDF link not available — contact your agency)" link that Gmail flags as a
     * suspicious redirect. Only touches system-default rows that still contain that exact
     * broken pattern; a Super Admin's customized copy (systemDefault=false, or a row that no
     * longer matches) is never overwritten. Idempotent — matches nothing on subsequent runs.
     */
    private void repairBrokenContractPdfButton() {
        List<EmailTemplate> broken = templateRepository.findAll().stream()
                .filter(t -> KEY_CONTRACT_SIGNED_CLIENT.equals(t.getTemplateKey()))
                .filter(t -> Boolean.TRUE.equals(t.getSystemDefault()))
                .filter(t -> t.getBodyHtml() != null && t.getBodyHtml().contains("href=\"{{contractPdfUrl}}\""))
                .toList();
        if (broken.isEmpty()) return;

        List<EmailTemplate> defaults = buildAllDefaults();
        for (EmailTemplate t : broken) {
            defaults.stream()
                    .filter(d -> d.getTemplateKey().equals(t.getTemplateKey()) && d.getLanguage().equals(t.getLanguage()))
                    .findFirst()
                    .ifPresent(d -> {
                        t.setBodyHtml(d.getBodyHtml());
                        t.setBodyText(d.getBodyText());
                        templateRepository.save(t);
                    });
        }
        log.info("[EMAIL_TEMPLATES] Repaired {} CONTRACT_SIGNED_CLIENT template row(s) with a broken PDF download button.", broken.size());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches the active template for key + language, falls back to EN if not found.
     */
    public Optional<EmailTemplate> getActive(String key, String language) {
        Optional<EmailTemplate> found = templateRepository
                .findByTemplateKeyAndLanguageAndIsActiveTrue(key, language != null ? language : "EN");
        if (found.isEmpty() && !"EN".equals(language)) {
            found = templateRepository.findByTemplateKeyAndLanguageAndIsActiveTrue(key, "EN");
        }
        return found;
    }

    /**
     * Renders an active template, substituting all known variables.
     * Falls back to EN if the requested language has no active template.
     *
     * @return rendered email, or empty if no template exists for this key.
     */
    public Optional<RenderedEmail> render(String key, String language, Map<String, String> vars) {
        return getActive(key, language).map(t -> {
            Map<String, String> enriched = enrich(vars);
            return new RenderedEmail(
                    replace(t.getSubject(), enriched),
                    replace(t.getBodyHtml(), enriched),
                    replace(t.getBodyText(), enriched));
        });
    }

    /**
     * Returns variable definitions for a given template key.
     * Each map has: name, description, example.
     */
    public List<Map<String, String>> getVariables(String templateKey) {
        return VARIABLE_DEFS.getOrDefault(templateKey, List.of());
    }

    /**
     * Returns all distinct template type strings.
     */
    public List<String> getAllTypes() {
        return List.of(
            CAT_AUTH, CAT_CONTRACT, CAT_RESERVATION,
            CAT_PAYMENT, CAT_SUPPORT, CAT_GPS, CAT_SYSTEM
        );
    }

    /**
     * Resets a template's content back to the built-in default.
     * Only works if the template has a templateKey.
     */
    @Transactional
    public boolean resetToDefault(Long id) {
        return templateRepository.findById(id).map(t -> {
            if (t.getTemplateKey() == null) return false;
            Optional<EmailTemplate> def = buildAllDefaults().stream()
                    .filter(d -> d.getTemplateKey().equals(t.getTemplateKey())
                              && d.getLanguage().equals(t.getLanguage()))
                    .findFirst();
            def.ifPresent(d -> {
                t.setSubject(d.getSubject());
                t.setBodyHtml(d.getBodyHtml());
                t.setBodyText(d.getBodyText());
                t.setIsActive(true);
                templateRepository.save(t);
            });
            return def.isPresent();
        }).orElse(false);
    }

    /**
     * Strips dangerous HTML — removes script tags and event handler attributes.
     */
    public String sanitizeHtml(String html) {
        if (html == null) return null;
        return html
                .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                .replaceAll("(?i)\\s+on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "")
                .replaceAll("(?i)\\s+on\\w+\\s*=\\s*[^\\s>]+", "");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String replace(String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() != null ? e.getValue() : "");
        }
        // Log any remaining unresolved placeholders in debug
        if (result.contains("{{")) {
            log.debug("[EMAIL_TEMPLATE] Unresolved variables in rendered output: {}",
                result.replaceAll("(?s).*(\\{\\{[^}]+\\}\\}).*", "$1"));
        }
        return result;
    }

    private Map<String, String> enrich(Map<String, String> vars) {
        Map<String, String> enriched = new HashMap<>(vars != null ? vars : Map.of());
        enriched.putIfAbsent("currentYear", String.valueOf(Year.now().getValue()));
        enriched.putIfAbsent("companyName", "Innovax Technologies");
        enriched.putIfAbsent("fromName", "RentCar");
        return enriched;
    }

    // ── Default template builders ─────────────────────────────────────────────

    private List<EmailTemplate> buildAllDefaults() {
        return List.of(
            buildWelcomeAgency(),
            buildWelcomeEmployee(),
            buildResetPassword(),
            buildNewDeviceLogin(),
            buildContractSignedClient(),
            buildReservationConfirmed(),
            buildPaymentReceived(),
            buildSubscriptionTrialEnding(),
            buildSubscriptionPaymentFailed(),
            buildSupportTicketCreated(),
            buildSupportTicketCreatedInternal(),
            buildSupportReply(),
            buildContactFormReceived(),
            buildGpsGeofenceAlert(),
            buildGpsMovementAlert()
        );
    }

    private EmailTemplate tpl(String key, String type, String name, String subject,
                               String htmlBody, String plainBody) {
        return EmailTemplate.builder()
                .templateKey(key)
                .type(type)
                .name(name)
                .language("EN")
                .subject(subject)
                .bodyHtml(htmlBody)
                .bodyText(plainBody)
                .isActive(true)
                .systemDefault(true)
                .createdBy("SYSTEM")
                .build();
    }

    // ── Shared HTML shell ─────────────────────────────────────────────────────

    private static String shell(String title, String subtitle, String bodyContent) {
        return "<div style=\"margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#0f172a;\">"
             + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:32px 16px;background:#f4f7fb;\">"
             + "<tr><td align=\"center\">"
             + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px;background:#ffffff;"
             + "border-radius:22px;overflow:hidden;box-shadow:0 20px 45px rgba(15,23,42,0.10);\">"
             // header
             + "<tr><td style=\"padding:28px 32px;background:linear-gradient(135deg,#071827 0%,#0f766e 100%);"
             + "color:#ffffff;\">"
             + "<div style=\"font-size:12px;letter-spacing:0.1em;text-transform:uppercase;opacity:0.80;\">"
             + "Innovax Technologies</div>"
             + "<h1 style=\"margin:10px 0 0;font-size:26px;line-height:1.25;font-weight:800;\">" + title + "</h1>"
             + "<p style=\"margin:10px 0 0;font-size:14px;line-height:1.7;color:#d7fffb;\">" + subtitle + "</p>"
             + "</td></tr>"
             // body
             + "<tr><td style=\"padding:32px;\">" + bodyContent + "</td></tr>"
             // footer
             + "<tr><td style=\"padding:20px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;text-align:center;\">"
             + "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">"
             + "Sent by RentCar &middot; Innovax Technologies &middot; {{currentYear}}<br>"
             + "<span style=\"font-size:11px;\">If you did not expect this email, please contact "
             + "<a href=\"mailto:support@innovacar.app\" style=\"color:#0f766e;text-decoration:none;\">support@innovacar.app</a></span>"
             + "</p></td></tr>"
             + "</table></td></tr></table></div>";
    }

    private static String cta(String label, String url) {
        return "<div style=\"text-align:center;margin:28px 0;\">"
             + "<a href=\"" + url + "\" style=\"display:inline-block;padding:14px 32px;background:linear-gradient(135deg,#0f766e,#10b981);"
             + "color:#ffffff;text-decoration:none;border-radius:12px;font-weight:700;font-size:15px;"
             + "letter-spacing:0.02em;box-shadow:0 4px 14px rgba(15,118,110,0.35);\">"
             + label + "</a></div>";
    }

    private static String infoBox(String... rows) {
        StringBuilder sb = new StringBuilder(
            "<div style=\"background:#f0fdf9;border-left:4px solid #10b981;border-radius:10px;padding:18px 20px;margin:20px 0;\">"
        );
        for (String row : rows) {
            sb.append("<p style=\"margin:6px 0;font-size:14px;color:#0f172a;\">").append(row).append("</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String alertBox(String... rows) {
        StringBuilder sb = new StringBuilder(
            "<div style=\"background:#fff7ed;border-left:4px solid #f97316;border-radius:10px;padding:18px 20px;margin:20px 0;\">"
        );
        for (String row : rows) {
            sb.append("<p style=\"margin:6px 0;font-size:14px;color:#0f172a;\">").append(row).append("</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String dangerBox(String... rows) {
        StringBuilder sb = new StringBuilder(
            "<div style=\"background:#fef2f2;border-left:4px solid #ef4444;border-radius:10px;padding:18px 20px;margin:20px 0;\">"
        );
        for (String row : rows) {
            sb.append("<p style=\"margin:6px 0;font-size:14px;color:#0f172a;\">").append(row).append("</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String para(String text) {
        return "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.7;color:#334155;\">" + text + "</p>";
    }

    private static String bold(String text) { return "<strong>" + text + "</strong>"; }
    private static String em(String text)   { return "<em>" + text + "</em>"; }

    // ══════════════════════════════════════════════════════════════════════════
    //  DEFAULT TEMPLATES
    // ══════════════════════════════════════════════════════════════════════════

    // A. WELCOME_AGENCY ───────────────────────────────────────────────────────
    private EmailTemplate buildWelcomeAgency() {
        String body = shell(
            "Welcome to RentCar!",
            "Your agency workspace is ready to use.",
            para("Hello " + bold("{{userName}}") + ",")
          + para("Your agency account " + bold("{{agencyName}}") + " has been successfully created on RentCar."
              + " You can now manage your vehicles, clients, reservations, contracts, payments, invoices, and more from one secure dashboard.")
          + infoBox(
              "&#128198; " + bold("Plan:") + " {{planName}}",
              "&#9200; " + bold("Trial ends:") + " {{trialEndDate}}",
              "&#128231; " + bold("Login email:") + " {{userEmail}}"
            )
          + cta("Open Your Dashboard", "{{dashboardUrl}}")
          + para("<span style=\"font-size:13px;color:#94a3b8;\">If you did not create this account, "
              + "please contact us immediately.</span>")
        );
        String plain = """
Hello {{userName}},

Your agency account {{agencyName}} has been created on RentCar.

Plan: {{planName}}
Trial ends: {{trialEndDate}}
Login email: {{userEmail}}

Open your dashboard: {{dashboardUrl}}

If you did not create this account, please contact support immediately.

— RentCar / Innovax Technologies
""";
        return tpl(KEY_WELCOME_AGENCY, CAT_AUTH,
                "Welcome — New Agency Account",
                "Welcome to RentCar — Your agency workspace is ready",
                body, plain);
    }

    // B. WELCOME_EMPLOYEE ─────────────────────────────────────────────────────
    private EmailTemplate buildWelcomeEmployee() {
        String body = shell(
            "You've Been Invited",
            "You now have access to your agency's RentCar workspace.",
            para("Hello " + bold("{{employeeName}}") + ",")
          + para("You have been added as a team member at " + bold("{{agencyName}}") + " on RentCar.")
          + infoBox(
              "&#128100; " + bold("Role:") + " {{roleName}}",
              "&#128231; " + bold("Login email:") + " {{loginEmail}}",
              "&#128274; " + bold("Temporary password:") + " {{temporaryPassword}}"
            )
          + dangerBox("&#128274; Please change your password immediately after your first login.")
          + cta("Login to RentCar", "{{dashboardUrl}}")
        );
        String plain = """
Hello {{employeeName}},

You have been invited to join {{agencyName}} on RentCar.

Role: {{roleName}}
Login email: {{loginEmail}}
Temporary password: {{temporaryPassword}}

IMPORTANT: Please change your password after your first login.

Login here: {{dashboardUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_WELCOME_EMPLOYEE, CAT_AUTH,
                "Welcome — New Employee Invitation",
                "You have been invited to RentCar",
                body, plain);
    }

    // C. RESET_PASSWORD ───────────────────────────────────────────────────────
    private EmailTemplate buildResetPassword() {
        String body = shell(
            "Reset Your Password",
            "We received a request to reset your RentCar password.",
            para("Hello " + bold("{{userName}}") + ",")
          + para("Click the button below to set a new password. This link expires in 30 minutes.")
          + cta("Reset My Password", "{{resetPasswordUrl}}")
          + dangerBox("If you did not request this, ignore this email. Your password will not change.")
          + para("<span style=\"font-size:13px;color:#94a3b8;\">Need help? "
              + "<a href=\"{{supportUrl}}\" style=\"color:#0f766e;\">Contact support</a></span>")
        );
        String plain = """
Hello {{userName}},

We received a request to reset your RentCar password.

Reset your password here (valid 30 min):
{{resetPasswordUrl}}

If you did not request this, ignore this email.

— RentCar / Innovax Technologies
""";
        return tpl(KEY_RESET_PASSWORD, CAT_AUTH,
                "Reset Password",
                "Reset your RentCar password",
                body, plain);
    }

    // D. NEW_DEVICE_LOGIN ─────────────────────────────────────────────────────
    private EmailTemplate buildNewDeviceLogin() {
        String body = shell(
            "New Login Detected",
            "A new device signed in to your account.",
            para("Hello " + bold("{{userName}}") + ",")
          + para("We detected a new login to your RentCar account from an unrecognized device.")
          + infoBox(
              "&#128187; " + bold("Device:") + " {{deviceName}}",
              "&#127758; " + bold("IP address:") + " {{ipAddress}}",
              "&#128336; " + bold("Time:") + " {{loginTime}}"
            )
          + para("If this was you, no action is needed.")
          + dangerBox("If this was NOT you, secure your account immediately.")
          + cta("Review Account Security", "{{securityUrl}}")
        );
        String plain = """
Hello {{userName}},

A new login was detected on your RentCar account.

Device: {{deviceName}}
IP: {{ipAddress}}
Time: {{loginTime}}

If this was not you, please secure your account: {{securityUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_NEW_DEVICE_LOGIN, CAT_AUTH,
                "Security — New Device Login",
                "New login detected on your RentCar account",
                body, plain);
    }

    // E. CONTRACT_SIGNED_CLIENT ───────────────────────────────────────────────
    private EmailTemplate buildContractSignedClient() {
        String body = shell(
            "Contract Signed ✓",
            "Your rental contract has been signed successfully.",
            para("Hello " + bold("{{clientName}}") + ",")
          + para("Your rental contract with " + bold("{{agencyName}}") + " has been signed and is now active.")
          + infoBox(
              "&#128196; " + bold("Contract:") + " {{contractNumber}}",
              "&#128663; " + bold("Vehicle:") + " {{vehicleName}} &mdash; {{plateNumber}}",
              "&#128197; " + bold("Period:") + " {{startDate}} &rarr; {{endDate}}",
              "&#128176; " + bold("Total:") + " {{totalAmount}}"
            )
          + "{{contractPdfSection}}"
          + para("<span style=\"font-size:13px;color:#94a3b8;\">Thank you for choosing "
              + bold("{{agencyName}}") + ". Drive safely!</span>")
        );
        String plain = """
Hello {{clientName}},

Your rental contract with {{agencyName}} has been signed.

Contract: {{contractNumber}}
Vehicle: {{vehicleName}} — {{plateNumber}}
Period: {{startDate}} to {{endDate}}
Total: {{totalAmount}}

{{contractPdfPlainSection}}

Thank you for choosing {{agencyName}}.

— RentCar / Innovax Technologies
""";
        return tpl(KEY_CONTRACT_SIGNED_CLIENT, CAT_CONTRACT,
                "Contract Signed — Client Confirmation",
                "Your rental contract is signed — {{contractNumber}}",
                body, plain);
    }

    // F. RESERVATION_CONFIRMED ────────────────────────────────────────────────
    private EmailTemplate buildReservationConfirmed() {
        String body = shell(
            "Reservation Confirmed ✓",
            "Your vehicle reservation has been confirmed.",
            para("Hello " + bold("{{clientName}}") + ",")
          + para("Your reservation with " + bold("{{agencyName}}") + " is confirmed.")
          + infoBox(
              "&#128196; " + bold("Reservation:") + " {{reservationNumber}}",
              "&#128663; " + bold("Vehicle:") + " {{vehicleName}}",
              "&#128197; " + bold("From:") + " {{startDate}}",
              "&#128197; " + bold("Until:") + " {{endDate}}",
              "&#127970; " + bold("Agency:") + " {{agencyName}}"
            )
          + para("Please bring a valid ID and driver&apos;s license when picking up your vehicle.")
          + cta("View My Reservation", "{{dashboardUrl}}")
        );
        String plain = """
Hello {{clientName}},

Your reservation {{reservationNumber}} with {{agencyName}} is confirmed.

Vehicle: {{vehicleName}}
From: {{startDate}}
Until: {{endDate}}

Please bring your ID and driver's license.

— RentCar / Innovax Technologies
""";
        return tpl(KEY_RESERVATION_CONFIRMED, CAT_RESERVATION,
                "Reservation Confirmed",
                "Reservation confirmed — {{reservationNumber}}",
                body, plain);
    }

    // G. PAYMENT_RECEIVED ─────────────────────────────────────────────────────
    private EmailTemplate buildPaymentReceived() {
        String body = shell(
            "Payment Received ✓",
            "We have recorded your payment.",
            para("Hello " + bold("{{clientName}}") + ",")
          + para("A payment has been recorded on your rental contract with " + bold("{{agencyName}}") + ".")
          + infoBox(
              "&#128196; " + bold("Contract:") + " {{contractNumber}}",
              "&#9989; " + bold("Amount paid:") + " {{paidAmount}}",
              "&#128178; " + bold("Total:") + " {{totalAmount}}",
              "&#9203; " + bold("Remaining:") + " {{remainingAmount}}"
            )
          + para("Thank you for your payment. If you have any questions, please contact your agency.")
          + cta("View Contract", "{{dashboardUrl}}")
        );
        String plain = """
Hello {{clientName}},

A payment has been recorded on contract {{contractNumber}} with {{agencyName}}.

Amount paid: {{paidAmount}}
Total: {{totalAmount}}
Remaining: {{remainingAmount}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_PAYMENT_RECEIVED, CAT_PAYMENT,
                "Payment Received",
                "Payment received — {{contractNumber}}",
                body, plain);
    }

    // H. SUBSCRIPTION_TRIAL_ENDING ────────────────────────────────────────────
    private EmailTemplate buildSubscriptionTrialEnding() {
        String body = shell(
            "Trial Ending Soon",
            "Your RentCar free trial is almost over.",
            para("Hello " + bold("{{agencyName}}") + ",")
          + para("Your free trial of " + bold("{{planName}}") + " ends on " + bold("{{trialEndDate}}") + ".")
          + para("After this date, access will be restricted unless you subscribe to a plan.")
          + alertBox(
              "&#8987; " + bold("Trial ends:") + " {{trialEndDate}}",
              "&#127775; " + bold("Current plan:") + " {{planName}}"
            )
          + cta("Upgrade My Plan", "{{dashboardUrl}}")
          + para("<span style=\"font-size:13px;color:#94a3b8;\">Questions? We are happy to help at "
              + "<a href=\"mailto:support@innovacar.app\" style=\"color:#0f766e;\">support@innovacar.app</a></span>")
        );
        String plain = """
Hello {{agencyName}},

Your RentCar free trial ({{planName}}) ends on {{trialEndDate}}.

Upgrade your plan before the trial ends to keep full access.

Manage subscription: {{dashboardUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_SUBSCRIPTION_TRIAL_ENDING, CAT_PAYMENT,
                "Subscription — Trial Ending",
                "Your RentCar trial ends soon",
                body, plain);
    }

    // I. SUBSCRIPTION_PAYMENT_FAILED ──────────────────────────────────────────
    private EmailTemplate buildSubscriptionPaymentFailed() {
        String body = shell(
            "Payment Failed",
            "We were unable to process your subscription payment.",
            para("Hello " + bold("{{agencyName}}") + ",")
          + para("We could not process the payment for your " + bold("{{planName}}") + " subscription.")
          + dangerBox(
              "&#10060; Payment failed — your subscription may be suspended.",
              "&#128196; Plan: {{planName}}"
            )
          + para("Please update your payment method or contact support to resolve this.")
          + cta("Update Payment Method", "{{paymentUrl}}")
          + para("<span style=\"font-size:13px;color:#94a3b8;\">Need help? "
              + "<a href=\"{{supportUrl}}\" style=\"color:#0f766e;\">Contact support</a></span>")
        );
        String plain = """
Hello {{agencyName}},

We could not process payment for your {{planName}} subscription.

Please update your payment method: {{paymentUrl}}

Need help? {{supportUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_SUBSCRIPTION_PAYMENT_FAILED, CAT_PAYMENT,
                "Subscription Payment Failed",
                "Subscription payment failed",
                body, plain);
    }

    // J. SUPPORT_TICKET_CREATED ───────────────────────────────────────────────
    private EmailTemplate buildSupportTicketCreated() {
        String body = shell(
            "We Got Your Message",
            "Our support team will respond shortly.",
            para("Hello " + bold("{{userName}}") + ",")
          + para("Thank you for contacting RentCar support. We have received your request and will respond as soon as possible.")
          + infoBox(
              "&#127916; " + bold("Ticket:") + " {{ticketNumber}}",
              "&#128336; " + bold("Estimated response:") + " within 24 hours"
            )
          + cta("View My Support Ticket", "{{supportUrl}}")
        );
        String plain = """
Hello {{userName}},

We received your support request. Ticket: {{ticketNumber}}
Our team will respond within 24 hours.

View your ticket: {{supportUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_SUPPORT_TICKET_CREATED, CAT_SUPPORT,
                "Support Ticket Created",
                "Support request received — {{ticketNumber}}",
                body, plain);
    }

    // J-bis. SUPPORT_TICKET_CREATED_INTERNAL ────────────────────────────────────
    private EmailTemplate buildSupportTicketCreatedInternal() {
        String body = shell(
            "New Support Request",
            "{{channel}} / {{category}} — {{priority}} priority",
            para(bold("Ticket:") + " {{ticketNumber}}")
          + infoBox(
              "&#128231; " + bold("Channel:") + " {{channel}}",
              "&#128193; " + bold("Category:") + " {{category}}",
              "&#128681; " + bold("Priority:") + " {{priority}}",
              "&#127970; " + bold("Agency:") + " {{agencyName}}",
              "&#128100; " + bold("Requester:") + " {{requesterName}} ({{requesterEmail}})"
            )
          + para(bold("Subject:") + " {{subject}}")
          + infoBox("{{message}}")
          + cta("Open in Dashboard", "{{dashboardUrl}}")
        );
        String plain = """
New support request — {{ticketNumber}}

Channel: {{channel}}
Category: {{category}}
Priority: {{priority}}
Agency: {{agencyName}}
Requester: {{requesterName}} ({{requesterEmail}})

Subject: {{subject}}
{{message}}

Open in dashboard: {{dashboardUrl}}
""";
        return tpl(KEY_SUPPORT_TICKET_CREATED_INTERNAL, CAT_SUPPORT,
                "Support Ticket Created (Internal)",
                "[{{priority}}] {{ticketNumber}} — {{subject}}",
                body, plain);
    }

    // K. SUPPORT_REPLY ────────────────────────────────────────────────────────
    private EmailTemplate buildSupportReply() {
        String body = shell(
            "New Reply from Support",
            "RentCar support has responded to your ticket.",
            para("Hello " + bold("{{userName}}") + ",")
          + para("Our support team has replied to your ticket " + bold("{{ticketNumber}}") + ".")
          + infoBox("&#128172; " + bold("Reply:") + " {{replyMessage}}")
          + cta("View Full Conversation", "{{supportUrl}}")
        );
        String plain = """
Hello {{userName}},

RentCar support replied to ticket {{ticketNumber}}:

{{replyMessage}}

View the full conversation: {{supportUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_SUPPORT_REPLY, CAT_SUPPORT,
                "Support Ticket Reply",
                "New reply from RentCar Support — {{ticketNumber}}",
                body, plain);
    }

    // L. CONTACT_FORM_RECEIVED ────────────────────────────────────────────────
    private EmailTemplate buildContactFormReceived() {
        String body = shell(
            "Message Received",
            "Thank you for reaching out to us.",
            para("Hello " + bold("{{userName}}") + ",")
          + para("We received your message and will get back to you shortly.")
          + infoBox("&#9989; Your message has been forwarded to our team.")
          + para("In the meantime, feel free to browse our documentation or contact us directly.")
          + cta("Visit Support Center", "{{supportUrl}}")
        );
        String plain = """
Hello {{userName}},

We received your message. Our team will get back to you shortly.

Support center: {{supportUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_CONTACT_FORM_RECEIVED, CAT_SUPPORT,
                "Contact Form Confirmation",
                "We received your message",
                body, plain);
    }

    // M. GPS_GEOFENCE_ALERT ───────────────────────────────────────────────────
    private EmailTemplate buildGpsGeofenceAlert() {
        String body = shell(
            "&#9888; Geofence Alert",
            "A vehicle has exited the allowed area.",
            para("Hello " + bold("{{agencyName}}") + ",")
          + para("One of your vehicles has moved outside of the designated area.")
          + dangerBox(
              "&#128663; " + bold("Vehicle:") + " {{vehicleName}} &mdash; {{plateNumber}}",
              "&#128205; " + bold("Location:") + " {{location}}",
              "&#128336; " + bold("Time:") + " {{alertTime}}"
            )
          + cta("Open GPS Dashboard", "{{dashboardUrl}}")
          + para("<span style=\"font-size:13px;color:#94a3b8;\">This is an automated GPS alert from RentCar.</span>")
        );
        String plain = """
GEOFENCE ALERT — {{agencyName}}

Vehicle {{vehicleName}} ({{plateNumber}}) has exited the allowed area.

Location: {{location}}
Time: {{alertTime}}

View on GPS dashboard: {{dashboardUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_GPS_GEOFENCE_ALERT, CAT_GPS,
                "GPS — Geofence Alert",
                "GPS alert — Vehicle outside allowed area",
                body, plain);
    }

    // N. GPS_MOVEMENT_ALERT ───────────────────────────────────────────────────
    private EmailTemplate buildGpsMovementAlert() {
        String body = shell(
            "&#128663; Movement Detected",
            "Unexpected movement detected on a vehicle.",
            para("Hello " + bold("{{agencyName}}") + ",")
          + para("Unexpected movement has been detected on a vehicle in your fleet.")
          + alertBox(
              "&#128663; " + bold("Vehicle:") + " {{vehicleName}} &mdash; {{plateNumber}}",
              "&#128205; " + bold("Location:") + " {{location}}",
              "&#128336; " + bold("Detected at:") + " {{alertTime}}"
            )
          + cta("Open GPS Dashboard", "{{dashboardUrl}}")
          + para("<span style=\"font-size:13px;color:#94a3b8;\">This is an automated GPS alert from RentCar.</span>")
        );
        String plain = """
MOVEMENT ALERT — {{agencyName}}

Unexpected movement detected: {{vehicleName}} ({{plateNumber}})

Location: {{location}}
Time: {{alertTime}}

View on GPS dashboard: {{dashboardUrl}}

— RentCar / Innovax Technologies
""";
        return tpl(KEY_GPS_MOVEMENT_ALERT, CAT_GPS,
                "GPS — Movement Alert",
                "GPS movement detected — {{vehicleName}}",
                body, plain);
    }

    // ── Variable definitions per template key ─────────────────────────────────

    private static final Map<String, List<Map<String, String>>> VARIABLE_DEFS = Map.ofEntries(
        Map.entry(KEY_WELCOME_AGENCY, List.of(
            v("userName",     "Admin full name",         "Mohamed Amddah"),
            v("agencyName",   "Agency display name",     "Innovacar Agency"),
            v("planName",     "Subscription plan name",  "Pro"),
            v("trialEndDate", "Trial expiry date",       "2026-08-01"),
            v("userEmail",    "Login email address",     "admin@innovacar.app"),
            v("dashboardUrl", "Link to the dashboard",   "https://app.innovacar.app")
        )),
        Map.entry(KEY_WELCOME_EMPLOYEE, List.of(
            v("employeeName",      "Employee full name",    "Karim Benali"),
            v("agencyName",        "Agency display name",   "Innovacar Agency"),
            v("roleName",          "Assigned role",         "Agent"),
            v("loginEmail",        "Employee login email",  "karim@innovacar.app"),
            v("temporaryPassword", "Temporary password",    "Temp@1234"),
            v("dashboardUrl",      "Link to login",         "https://app.innovacar.app")
        )),
        Map.entry(KEY_RESET_PASSWORD, List.of(
            v("userName",        "User full name",       "Mohamed Amddah"),
            v("resetPasswordUrl","Password reset link",  "https://app.innovacar.app/reset/abc123"),
            v("supportUrl",      "Support page URL",     "https://app.innovacar.app/support")
        )),
        Map.entry(KEY_NEW_DEVICE_LOGIN, List.of(
            v("userName",    "User full name",       "Mohamed Amddah"),
            v("deviceName",  "Device / browser",     "Chrome on Windows"),
            v("ipAddress",   "IP address",           "192.168.1.1"),
            v("loginTime",   "Login timestamp",      "2026-07-01 14:30"),
            v("securityUrl", "Security settings URL","https://app.innovacar.app/settings/security")
        )),
        Map.entry(KEY_CONTRACT_SIGNED_CLIENT, List.of(
            v("clientName",     "Client full name",     "Ahmed Yacoubi"),
            v("contractNumber", "Contract reference",   "CTR-2026-00002"),
            v("vehicleName",    "Vehicle make/model",   "Hyundai i20"),
            v("plateNumber",    "Plate number",         "12345-A-6"),
            v("startDate",      "Rental start date",    "2026-07-01"),
            v("endDate",        "Rental end date",      "2026-07-05"),
            v("totalAmount",    "Total price",          "2400 MAD"),
            v("agencyName",     "Agency name",          "Innovacar Agency"),
            v("contractPdfUrl", "PDF download link (raw URL, blank if not ready)", "https://api.innovacar.app/api/public/contracts/1/demo-token/pdf"),
            v("contractPdfSection", "PDF download button or fallback message (HTML)", "<a href=\"...\">Download Contract PDF</a>"),
            v("contractPdfPlainSection", "PDF download line or fallback message (plain text)", "Download your contract PDF:\nhttps://api.innovacar.app/...")
        )),
        Map.entry(KEY_RESERVATION_CONFIRMED, List.of(
            v("clientName",       "Client full name",     "Ahmed Yacoubi"),
            v("reservationNumber","Reservation reference","RES-2026-00010"),
            v("vehicleName",      "Vehicle make/model",   "Hyundai i20"),
            v("startDate",        "Rental start date",    "2026-07-01"),
            v("endDate",          "Rental end date",      "2026-07-05"),
            v("agencyName",       "Agency name",          "Innovacar Agency"),
            v("dashboardUrl",     "Dashboard URL",        "https://app.innovacar.app")
        )),
        Map.entry(KEY_PAYMENT_RECEIVED, List.of(
            v("clientName",     "Client full name",   "Ahmed Yacoubi"),
            v("contractNumber", "Contract reference", "CTR-2026-00002"),
            v("paidAmount",     "Amount paid",        "1200 MAD"),
            v("totalAmount",    "Total price",        "2400 MAD"),
            v("remainingAmount","Amount remaining",   "1200 MAD"),
            v("agencyName",     "Agency name",        "Innovacar Agency"),
            v("dashboardUrl",   "Dashboard URL",      "https://app.innovacar.app")
        )),
        Map.entry(KEY_SUBSCRIPTION_TRIAL_ENDING, List.of(
            v("agencyName",   "Agency name",     "Innovacar Agency"),
            v("planName",     "Plan name",       "Pro"),
            v("trialEndDate", "Trial end date",  "2026-08-01"),
            v("dashboardUrl", "Dashboard URL",   "https://app.innovacar.app")
        )),
        Map.entry(KEY_SUBSCRIPTION_PAYMENT_FAILED, List.of(
            v("agencyName", "Agency name",         "Innovacar Agency"),
            v("planName",   "Plan name",           "Pro"),
            v("paymentUrl", "Payment update link", "https://app.innovacar.app/billing"),
            v("supportUrl", "Support URL",         "https://app.innovacar.app/support")
        )),
        Map.entry(KEY_SUPPORT_TICKET_CREATED, List.of(
            v("userName",     "User full name",   "Mohamed Amddah"),
            v("ticketNumber", "Ticket reference", "TKT-2026-00001"),
            v("supportUrl",   "Support URL",      "https://app.innovacar.app/support")
        )),
        Map.entry(KEY_SUPPORT_TICKET_CREATED_INTERNAL, List.of(
            v("ticketNumber",    "Ticket reference",   "TKT-2026-00001"),
            v("channel",         "Routing channel",    "TECHNICAL"),
            v("category",        "Ticket category",    "GPS"),
            v("priority",        "Priority",            "HIGH"),
            v("agencyName",      "Agency name",         "Innovacar Agency"),
            v("requesterName",   "Requester name",      "Mohamed Amddah"),
            v("requesterEmail",  "Requester email",     "mohamed@example.com"),
            v("subject",         "Ticket subject",      "GPS not tracking"),
            v("message",         "Ticket message",      "Our vehicle GPS stopped reporting location."),
            v("dashboardUrl",    "Dashboard URL",       "https://app.innovacar.app/super-admin/support")
        )),
        Map.entry(KEY_SUPPORT_REPLY, List.of(
            v("userName",     "User full name",  "Mohamed Amddah"),
            v("ticketNumber", "Ticket ref",      "TKT-2026-00001"),
            v("replyMessage", "Reply content",   "We have reviewed your request and will resolve it shortly."),
            v("supportUrl",   "Support URL",     "https://app.innovacar.app/support")
        )),
        Map.entry(KEY_CONTACT_FORM_RECEIVED, List.of(
            v("userName",   "User name",    "Mohamed Amddah"),
            v("supportUrl", "Support URL",  "https://app.innovacar.app/support")
        )),
        Map.entry(KEY_GPS_GEOFENCE_ALERT, List.of(
            v("agencyName",  "Agency name",     "Innovacar Agency"),
            v("vehicleName", "Vehicle name",    "Hyundai i20"),
            v("plateNumber", "Plate number",    "12345-A-6"),
            v("location",    "GPS coordinates", "33.5731° N, 7.5898° W"),
            v("alertTime",   "Alert timestamp", "2026-07-01 14:30"),
            v("dashboardUrl","Dashboard URL",   "https://app.innovacar.app")
        )),
        Map.entry(KEY_GPS_MOVEMENT_ALERT, List.of(
            v("agencyName",  "Agency name",     "Innovacar Agency"),
            v("vehicleName", "Vehicle name",    "Hyundai i20"),
            v("plateNumber", "Plate number",    "12345-A-6"),
            v("location",    "GPS coordinates", "33.5731° N, 7.5898° W"),
            v("alertTime",   "Alert timestamp", "2026-07-01 14:30"),
            v("dashboardUrl","Dashboard URL",   "https://app.innovacar.app")
        ))
    );

    private static Map<String, String> v(String name, String desc, String example) {
        return Map.of("name", name, "description", desc, "example", example);
    }
}
