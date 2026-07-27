package com.carrental.security.oauth2;

/**
 * Normalizes the configured {@code app.frontend-url} before it's used to
 * build an OAuth2 redirect target. Vercel's canonical redirect (see
 * frontend-web/vercel.json) permanently redirects www.innovacar.app ->
 * innovacar.app; if {@code app.frontend-url} were ever set to the www host,
 * every OAuth2 callback redirect would take an extra 30x hop through that
 * canonicalization before the SPA ever loads — one more place for a slow
 * network/cold start to eat into the short-lived exchange code's TTL.
 * Redirecting straight to the canonical host removes that hop entirely,
 * regardless of what FRONTEND_URL/APP_FRONTEND_URL/PUBLIC_WEB_URL happens to
 * resolve to in a given environment.
 */
final class FrontendUrls {

    private FrontendUrls() {}

    static String canonicalize(String frontendUrl) {
        String trimmed = frontendUrl.replaceAll("/+$", "");
        return trimmed.replaceFirst("^(https?://)www\\.", "$1");
    }
}
