package com.carrental.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Fails startup fast and loudly if {@code app.frontend-url} or
 * {@code app.public-api-url} would resolve to a loopback/private/insecure
 * host in a deployed environment, instead of ever silently emailing a real
 * user a {@code http://localhost:8082/...} link (the original production
 * incident this guards against).
 *
 * <p>This check does not create the outage by itself — {@code
 * application.yml}'s base default for both properties is now the real
 * production domain (not localhost), so a missing Railway env var degrades
 * to "still correct", not to "refuses to start". This initializer exists to
 * catch a genuinely wrong override (e.g. someone sets {@code PUBLIC_API_URL}
 * to an internal/loopback address by mistake), not to be the thing standing
 * between a missing env var and production staying up.
 *
 * <p>Mirrors {@link DatabaseSafetyInitializer}'s pattern: runs before any bean
 * is created, so a misconfigured deployment never gets as far as sending a
 * broken email — it fails the deploy outright with a clear, actionable error.
 */
public class PublicUrlSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private record Rule(String property, String envVarHint, Set<String> allowedHosts) {}

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        boolean testProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("test"::equalsIgnoreCase);
        if (testProfile) {
            return;
        }

        boolean onRailway = System.getenv("RAILWAY_ENVIRONMENT_NAME") != null
                || System.getenv("RAILWAY_PROJECT_ID") != null
                || System.getenv("RAILWAY_SERVICE_ID") != null;
        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.toLowerCase(Locale.ROOT).contains("prod"));
        String activeProfiles = String.join(",", environment.getActiveProfiles());

        // Local dev (no Railway, no prod profile) is exactly where a
        // http://localhost:... value is expected and correct — only enforce
        // this once the app is actually being deployed.
        if (!onRailway && !prodProfile) {
            return;
        }

        String context = "activeProfiles=" + (activeProfiles.isBlank() ? "(none)" : activeProfiles)
                + " onRailway=" + onRailway;

        checkPublicUrl(environment, new Rule("app.frontend-url", "APP_FRONTEND_URL (or FRONTEND_URL / PUBLIC_WEB_URL)",
                Set.of("innovacar.app", "www.innovacar.app")), context);
        checkPublicUrl(environment, new Rule("app.public-api-url", "PUBLIC_API_URL",
                Set.of("api.innovacar.app")), context);

        System.out.println("[PUBLIC_URL_CONFIG] frontendHost=" + safeHost(environment, "app.frontend-url")
                + " apiHost=" + safeHost(environment, "app.public-api-url") + " " + context);
    }

    private String safeHost(ConfigurableEnvironment environment, String property) {
        try {
            return URI.create(environment.getProperty(property, "").trim()).getHost();
        } catch (Exception e) {
            return "(unparseable)";
        }
    }

    private void checkPublicUrl(ConfigurableEnvironment environment, Rule rule, String context) {
        String value = environment.getProperty(rule.property(), "");
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "PUBLIC_URL_MISSING: " + rule.property() + " could not be resolved (" + context + "). Set "
                            + rule.envVarHint() + " in Railway Variables to the real production HTTPS domain — "
                            + "refusing to start rather than fall back to a localhost default that would leak "
                            + "into emails/links sent to real users.");
        }
        String trimmed = value.trim().replaceAll("/+$", "");

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            throw invalid(rule, trimmed, context, "is not a valid URL");
        }

        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null || scheme == null) {
            throw invalid(rule, trimmed, context, "is missing a scheme or host");
        }
        if (uri.getUserInfo() != null) {
            throw invalid(rule, trimmed, context, "must not contain user-info (username/password in the URL)");
        }
        if (uri.getRawQuery() != null) {
            throw invalid(rule, trimmed, context, "must not contain a query string");
        }
        if (uri.getRawFragment() != null) {
            throw invalid(rule, trimmed, context, "must not contain a fragment");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (isLoopbackOrPrivate(lowerHost)) {
            throw new IllegalStateException(
                    "REFUSING_LOCALHOST_PUBLIC_URL: " + rule.property() + " resolves to host '" + host
                            + "' while deployed (" + context + "). Set " + rule.envVarHint() + " in Railway "
                            + "Variables to the real public HTTPS domain — this value gets embedded directly in "
                            + "outbound emails and links, so it must never be a loopback, private, or internal "
                            + "address in a deployed environment.");
        }

        if (!"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(
                    "REFUSING_INSECURE_PUBLIC_URL: " + rule.property() + " resolves to scheme '" + scheme
                            + "' while deployed (" + context + "). Set " + rule.envVarHint()
                            + " in Railway Variables to an https:// URL — production links must never be "
                            + "served over plain http.");
        }

        if (!rule.allowedHosts().contains(lowerHost)) {
            throw new IllegalStateException(
                    "UNEXPECTED_PUBLIC_URL_HOST: " + rule.property() + " resolves to host '" + host
                            + "' while deployed (" + context + "), which is not one of the expected production "
                            + "hosts (" + String.join(", ", rule.allowedHosts()) + "). Set " + rule.envVarHint()
                            + " in Railway Variables to the correct domain, or update this allow-list if the "
                            + "domain has genuinely changed.");
        }
    }

    private boolean isLoopbackOrPrivate(String lowerHost) {
        return lowerHost.equals("localhost")
                || lowerHost.equals("0.0.0.0")
                || lowerHost.equals("host.docker.internal")
                || lowerHost.contains("railway.internal")
                || lowerHost.startsWith("127.")
                || lowerHost.startsWith("192.168.")
                || lowerHost.startsWith("10.")
                || lowerHost.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    private IllegalStateException invalid(Rule rule, String value, String context, String reason) {
        return new IllegalStateException(
                "PUBLIC_URL_INVALID: " + rule.property() + " resolved to '" + value + "' (" + context + "), which "
                        + reason + ". Set " + rule.envVarHint() + " in Railway Variables to a clean absolute "
                        + "https:// origin with no trailing slash, path, query, or fragment.");
    }
}
