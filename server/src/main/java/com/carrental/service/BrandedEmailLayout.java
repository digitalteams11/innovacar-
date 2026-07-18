package com.carrental.service;

/**
 * The one shared HTML shell every transactional email renders through — header
 * (brand mark + title), a 640px rounded card body, and a footer with support
 * contact + year. Table-based layout with fully inlined CSS on purpose: Gmail,
 * Outlook and most mobile mail clients strip {@code <style>} blocks and ignore
 * flexbox/grid, so nothing here relies on either. No external images or fonts,
 * no JavaScript — the brand mark is a CSS-only monogram badge so the header
 * still reads correctly even when a client blocks remote images.
 *
 * <p>{@link EmailTemplateService} and {@link EmailService} both render through
 * this class so every email in the product shares one visual identity instead
 * of each call site reinventing its own colors/spacing.
 */
final class BrandedEmailLayout {

    static final String DEFAULT_COMPANY_NAME = "Innovax Technologies";
    static final String DEFAULT_SUPPORT_EMAIL = "support@innovacar.app";

    private BrandedEmailLayout() {
    }

    /** Full HTML document: shell(...) wrapped in {@code <!DOCTYPE html>}/{@code <html>}/{@code <body>}. */
    static String document(String title, String subtitle, String bodyContent) {
        return document(title, subtitle, bodyContent, DEFAULT_COMPANY_NAME, DEFAULT_SUPPORT_EMAIL);
    }

    static String document(String title, String subtitle, String bodyContent,
                            String companyName, String supportEmail) {
        return "<!DOCTYPE html>"
             + "<html lang=\"en\">"
             + "<head>"
             + "<meta charset=\"UTF-8\">"
             + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
             + "<meta name=\"color-scheme\" content=\"light\">"
             + "<title>" + escape(title) + "</title>"
             + "</head>"
             + "<body style=\"margin:0;padding:0;\">"
             + shell(title, subtitle, bodyContent, companyName, supportEmail)
             + "</body></html>";
    }

    /** The card fragment alone (no {@code <html>/<body>}) — used where the caller wraps its own document. */
    static String shell(String title, String subtitle, String bodyContent) {
        return shell(title, subtitle, bodyContent, DEFAULT_COMPANY_NAME, DEFAULT_SUPPORT_EMAIL);
    }

    static String shell(String title, String subtitle, String bodyContent,
                         String companyName, String supportEmail) {
        String year = String.valueOf(java.time.Year.now().getValue());
        return "<div style=\"margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#0f172a;\">"
             + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:32px 16px;background:#f4f7fb;\">"
             + "<tr><td align=\"center\">"
             + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;background:#ffffff;"
             + "border-radius:22px;overflow:hidden;box-shadow:0 20px 45px rgba(15,23,42,0.10);\">"
             // header — CSS-only monogram badge, no <img>, so it never breaks when images are blocked
             + "<tr><td style=\"padding:28px 32px;background:linear-gradient(135deg,#071827 0%,#0f766e 100%);color:#ffffff;\">"
             + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
             + "<td style=\"width:40px;height:40px;background:#10b981;border-radius:10px;text-align:center;"
             + "vertical-align:middle;font-weight:800;font-size:16px;color:#071827;\" role=\"img\" aria-label=\"" + escape(companyName) + " logo\">IC</td>"
             + "<td style=\"padding-left:12px;\">"
             + "<div style=\"font-size:12px;letter-spacing:0.1em;text-transform:uppercase;opacity:0.80;\">" + escape(companyName) + "</div>"
             + "<div style=\"font-size:15px;font-weight:700;\">Innovacar</div>"
             + "</td></tr></table>"
             + "<h1 style=\"margin:18px 0 0;font-size:24px;line-height:1.3;font-weight:800;\">" + title + "</h1>"
             + (subtitle != null && !subtitle.isBlank()
                ? "<p style=\"margin:10px 0 0;font-size:14px;line-height:1.7;color:#d7fffb;\">" + subtitle + "</p>"
                : "")
             + "</td></tr>"
             // body
             + "<tr><td style=\"padding:32px;\">" + bodyContent + "</td></tr>"
             // footer
             + "<tr><td style=\"padding:20px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;text-align:center;\">"
             + "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">"
             + "Sent by Innovacar &middot; " + escape(companyName) + " &middot; " + year + "<br>"
             + "<span style=\"font-size:11px;\">Need help? Contact "
             + "<a href=\"mailto:" + supportEmail + "\" style=\"color:#0f766e;text-decoration:none;\">" + supportEmail + "</a></span>"
             + "</p></td></tr>"
             + "</table></td></tr></table></div>";
    }

    /**
     * The big centered verification-code card: label above, large letter-spaced
     * digits below. This is the visual focus of any OTP/verification email.
     */
    static String codeBox(String label, String code) {
        return "<div style=\"background:#f0fdf9;border:2px dashed #0f766e;border-radius:14px;"
             + "padding:26px 20px;text-align:center;margin:24px 0;\">"
             + "<p style=\"margin:0 0 10px;font-size:12px;font-weight:700;letter-spacing:0.08em;"
             + "text-transform:uppercase;color:#0f766e;\">" + escape(label) + "</p>"
             + "<span style=\"font-size:40px;font-weight:800;letter-spacing:10px;color:#071827;"
             + "font-family:'Courier New',Courier,monospace;\">" + escape(code) + "</span>"
             + "</div>";
    }

    /** Solid call-to-action button — degrades to a plain underlined link if images/styles are stripped. */
    static String cta(String label, String url) {
        return "<div style=\"text-align:center;margin:28px 0;\">"
             + "<a href=\"" + url + "\" style=\"display:inline-block;padding:14px 32px;"
             + "background:linear-gradient(135deg,#0f766e,#10b981);color:#ffffff;text-decoration:none;"
             + "border-radius:12px;font-weight:700;font-size:15px;letter-spacing:0.02em;"
             + "box-shadow:0 4px 14px rgba(15,118,110,0.35);\">" + escape(label) + "</a></div>";
    }

    static String infoBox(String... rows) {
        return colorBox("#f0fdf9", "#10b981", rows);
    }

    static String alertBox(String... rows) {
        return colorBox("#fff7ed", "#f97316", rows);
    }

    private static String colorBox(String background, String accent, String... rows) {
        StringBuilder sb = new StringBuilder(
            "<div style=\"background:" + background + ";border-left:4px solid " + accent + ";"
            + "border-radius:10px;padding:18px 20px;margin:20px 0;\">"
        );
        for (String row : rows) {
            sb.append("<p style=\"margin:6px 0;font-size:14px;color:#0f172a;\">").append(row).append("</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
