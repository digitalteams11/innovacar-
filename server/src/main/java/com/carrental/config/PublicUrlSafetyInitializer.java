package com.carrental.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/**
 * Fails startup fast and loudly if {@code app.frontend-url} or
 * {@code app.public-api-url} would resolve to a loopback/private host in a
 * deployed environment, instead of ever silently emailing a real user a
 * {@code http://localhost:8082/...} link (the exact production incident this
 * guards against — the "Download Contract PDF" button in the contract-signed
 * email pointed at localhost because {@code PUBLIC_API_URL} was never set on
 * Railway, so the property silently fell back to its local-dev default).
 *
 * <p>Mirrors {@link DatabaseSafetyInitializer}'s pattern: runs before any bean
 * is created, so a misconfigured deployment never gets as far as sending a
 * broken email — it fails the deploy outright with a clear, actionable error.
 */
public class PublicUrlSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

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

        // Local dev (no Railway, no prod profile) is exactly where a
        // http://localhost:... value is expected and correct — only enforce
        // this once the app is actually being deployed.
        if (!onRailway && !prodProfile) {
            return;
        }

        checkPublicUrl(environment, "app.frontend-url", "FRONTEND_URL (or PUBLIC_WEB_URL)");
        checkPublicUrl(environment, "app.public-api-url", "PUBLIC_API_URL");
    }

    private void checkPublicUrl(ConfigurableEnvironment environment, String property, String envVarHint) {
        String value = environment.getProperty(property, "");
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "PUBLIC_URL_MISSING: " + property + " could not be resolved. Set " + envVarHint
                            + " to the real production HTTPS domain — refusing to start rather than fall back "
                            + "to a localhost default that would leak into emails/links sent to real users.");
        }

        String host;
        String scheme;
        try {
            URI uri = URI.create(value.trim());
            host = uri.getHost();
            scheme = uri.getScheme();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "PUBLIC_URL_INVALID: " + property + " resolved to '" + value + "', which is not a valid URL.");
        }

        if (host == null || scheme == null) {
            throw new IllegalStateException(
                    "PUBLIC_URL_INVALID: " + property + " resolved to '" + value
                            + "', which is missing a scheme or host.");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);
        boolean isLoopbackOrPrivate = lowerHost.equals("localhost")
                || lowerHost.startsWith("127.")
                || lowerHost.startsWith("192.168.")
                || lowerHost.equals("0.0.0.0")
                || lowerHost.contains("railway.internal");
        if (isLoopbackOrPrivate) {
            throw new IllegalStateException(
                    "REFUSING_LOCALHOST_PUBLIC_URL: " + property + " resolves to host '" + host
                            + "' while deployed (onRailway/prod). Set " + envVarHint + " to the real public "
                            + "HTTPS domain — this value gets embedded directly in outbound emails and links, "
                            + "so it must never be a loopback or internal address in a deployed environment.");
        }

        if (!"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(
                    "REFUSING_INSECURE_PUBLIC_URL: " + property + " resolves to scheme '" + scheme
                            + "' while deployed (onRailway/prod). Set " + envVarHint
                            + " to an https:// URL — production links must never be served over plain http.");
        }

        System.out.println("[PUBLIC_URL_CONFIG_DEBUG] " + property + " host=" + host + " scheme=" + scheme);
    }
}
