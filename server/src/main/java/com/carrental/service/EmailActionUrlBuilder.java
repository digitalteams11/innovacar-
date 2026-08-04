package com.carrental.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * The single place every transactional email builds an Innovacar action URL.
 * Before this existed, every email-sending service concatenated
 * {@code frontendUrl + "/some/path"} independently — several of them forgot
 * the HashRouter's required {@code /#/} prefix (the app is served behind
 * {@code <HashRouter>}, see frontend-web/src/main.tsx), producing CTA buttons
 * that looked correct in the email but opened the bare marketing/dashboard
 * shell instead of the intended route, or in production/report emails often
 * do this correctly if the plain `/some/path` link were dereferenced by a
 * server, but this SPA is served client-side, so a non-hash path resolves to
 * "/" once the (empty) hash is parsed by HashRouter — the exact bug behind
 * the broken "Open dashboard" button.
 *
 * <p>Every method here returns an absolute {@code https://} URL rooted at
 * {@code app.frontend-url} (see application.yml — resolves to
 * {@code https://www.innovacar.app} in production, guarded against
 * localhost/private-network values by {@link com.carrental.config.PublicUrlSafetyInitializer}
 * at startup) with exactly one {@code /#/} segment and every dynamic path
 * segment / query value URL-encoded exactly once.
 */
@Service
public class EmailActionUrlBuilder {

    private final String frontendUrl;

    public EmailActionUrlBuilder(@Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        // Strip any trailing slashes once, here, so no call site has to.
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    /** Base "https://innovacar.app" with no trailing slash — for building a non-hash link (public API docs, mailto, etc). */
    public String origin() {
        return frontendUrl;
    }

    /** Innovacar dashboard — the actual existing route (App.tsx: "/" and "/dashboard" both render Dashboard). */
    public String dashboardUrl() {
        return hashUrl("/");
    }

    public String reportArchiveUrl() {
        return hashUrl("/report-archive");
    }

    /** report-archive has no per-report detail route today — reportId is carried as a query param the page can read/highlight, not a path segment. */
    public String reportArchiveUrl(Long reportId) {
        return reportId == null ? reportArchiveUrl() : hashUrl("/report-archive", "reportId", String.valueOf(reportId));
    }

    public String contractsUrl() {
        return hashUrl("/contracts");
    }

    public String contractUrl(Long contractId) {
        return hashUrl("/contracts/" + encodePathSegment(String.valueOf(contractId)));
    }

    public String invoicesUrl() {
        return hashUrl("/invoices");
    }

    public String invoiceUrl(Long invoiceId) {
        // No per-invoice detail route exists in App.tsx today — land on the list, matching reportArchiveUrl(id)'s pattern.
        return invoiceId == null ? invoicesUrl() : hashUrl("/invoices", "invoiceId", String.valueOf(invoiceId));
    }

    public String reservationsUrl() {
        return hashUrl("/reservations");
    }

    public String reservationUrl(Long reservationId) {
        return reservationId == null ? reservationsUrl() : hashUrl("/reservations", "reservationId", String.valueOf(reservationId));
    }

    public String subscriptionUrl() {
        return hashUrl("/subscription");
    }

    public String supportTicketUrl(Long ticketId) {
        return hashUrl("/tickets/" + encodePathSegment(String.valueOf(ticketId)));
    }

    /** No /super-admin/contact-requests/:id detail route exists — the list is the real destination. */
    public String contactRequestsAdminUrl() {
        return hashUrl("/super-admin/contact-requests");
    }

    public String supportTicketAdminUrl(Long ticketId) {
        return hashUrl("/super-admin/support/" + encodePathSegment(String.valueOf(ticketId)));
    }

    public String contactUrl() {
        return hashUrl("/contact");
    }

    // ── Public token links (no login required — no returnTo needed) ─────────

    public String clientInformationUrl(String rawToken) {
        return hashUrl("/client-info/" + encodePathSegment(rawToken));
    }

    public String contractSignatureUrl(Long contractId, String rawToken) {
        return hashUrl("/contract-sign/" + encodePathSegment(String.valueOf(contractId)) + "/" + encodePathSegment(rawToken));
    }

    public String additionalDriverSignatureUrl(String rawToken) {
        return hashUrl("/sign/additional-driver/" + encodePathSegment(rawToken));
    }

    public String passwordResetUrl(String rawToken) {
        return hashUrl("/reset-password", "token", rawToken);
    }

    public String emailVerificationUrl(String rawToken) {
        return hashUrl("/verify-email", "token", rawToken);
    }

    // ── Authenticated links that should return here after login ────────────

    /**
     * Wraps an already-built authenticated destination (any of the *Url()
     * methods above) behind {@code /#/login?returnTo=...} — ProtectedRoute
     * sends an unauthenticated visitor to Login anyway; this makes Login
     * send them back to where the email actually pointed instead of the
     * generic dashboard once they sign in. Only ever wraps an
     * already-hash-prefixed Innovacar URL built by this class, so there is
     * no open-redirect risk — see LoginReturnToGuard on the frontend for the
     * matching same-origin-only validation.
     */
    public String withLoginReturnTo(String destinationUrl) {
        String hashPath = extractHashPath(destinationUrl);
        return hashUrl("/login", "returnTo", hashPath);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private String hashUrl(String path) {
        return frontendUrl + "/#" + path;
    }

    private String hashUrl(String path, String queryKey, String queryValue) {
        return frontendUrl + "/#" + path + "?" + encodeQueryComponent(queryKey) + "=" + encodeQueryComponent(queryValue);
    }

    private String extractHashPath(String innovacarUrl) {
        int idx = innovacarUrl.indexOf("/#");
        return idx >= 0 ? innovacarUrl.substring(idx + 2) : "/";
    }

    private static String encodePathSegment(String raw) {
        return raw == null ? "" : UriUtils.encodePathSegment(raw, StandardCharsets.UTF_8);
    }

    private static String encodeQueryComponent(String raw) {
        return raw == null ? "" : UriUtils.encodeQueryParam(raw, StandardCharsets.UTF_8);
    }
}
