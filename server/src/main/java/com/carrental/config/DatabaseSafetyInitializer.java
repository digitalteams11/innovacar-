package com.carrental.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Fails startup fast and loudly instead of ever silently connecting to a
 * localhost database in a deployed environment. Runs before the DataSource
 * bean is created (as an ApplicationContextInitializer), so a misconfigured
 * deployment never gets as far as "Connection to localhost:5432 refused".
 */
public class DatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Set<String> DANGEROUS_DDL = Set.of("create", "create-drop", "drop");
    private static final Set<String> LOCALHOST_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0.0.0.0");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        String profile = String.join(",", environment.getActiveProfiles());
        if (profile.isBlank()) {
            profile = environment.getProperty("spring.profiles.active", "default");
        }
        boolean testProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("test"::equalsIgnoreCase);

        // Railway always injects these, regardless of which Spring profile ends up
        // active — this is the hard backstop against ever silently reaching for a
        // local database if SPRING_PROFILES_ACTIVE was left unset on the platform.
        boolean onRailway = System.getenv("RAILWAY_ENVIRONMENT_NAME") != null
                || System.getenv("RAILWAY_PROJECT_ID") != null
                || System.getenv("RAILWAY_SERVICE_ID") != null;

        String url = resolveQuietly(environment, "spring.datasource.url");
        String username = resolveQuietly(environment, "spring.datasource.username");
        String password = resolveQuietly(environment, "spring.datasource.password");
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", "none")
                .trim().toLowerCase(Locale.ROOT);

        System.out.println("[DB_CONFIG_DEBUG] profile=" + profile
                + " onRailway=" + onRailway
                + " urlPresent=" + !url.isBlank()
                + " usernamePresent=" + !username.isBlank()
                + " passwordPresent=" + !password.isBlank()
                + " host=" + safeHost(url));

        if (testProfile) {
            return;
        }

        if (url.isBlank()) {
            throw new IllegalStateException(
                    "DATABASE_URL_MISSING: spring.datasource.url could not be resolved. "
                            + "Set SPRING_DATASOURCE_URL to a jdbc:postgresql://HOST:PORT/DB URL, or set "
                            + "DATABASE_URL (postgres://user:pass@host:port/db — auto-converted at startup), "
                            + "as environment variables. Refusing to start rather than fall back to localhost.");
        }
        if (username.isBlank()) {
            throw new IllegalStateException(
                    "DATABASE_USERNAME_MISSING: spring.datasource.username could not be resolved. "
                            + "Set SPRING_DATASOURCE_USERNAME (or DATABASE_URL, which is auto-converted).");
        }
        if (password.isBlank()) {
            throw new IllegalStateException(
                    "DATABASE_PASSWORD_MISSING: spring.datasource.password is empty. "
                            + "Set the SPRING_DATASOURCE_PASSWORD environment variable to your local "
                            + "Postgres password before running, e.g. (PowerShell):\n"
                            + "  $env:SPRING_DATASOURCE_PASSWORD=\"<your-local-postgres-password>\"\n"
                            + "  mvn spring-boot:run\n"
                            + "See server/.env.example for all local dev environment variables.");
        }

        String host = safeHost(url);
        if (LOCALHOST_HOSTS.contains(host) && (onRailway || profile.toLowerCase(Locale.ROOT).contains("prod"))) {
            throw new IllegalStateException(
                    "REFUSING_LOCALHOST_DATABASE: spring.datasource.url resolves to host '" + host
                            + "' while profile='" + profile + "' onRailway=" + onRailway + ". This means "
                            + "SPRING_DATASOURCE_URL / DATABASE_URL was not set and a local default leaked "
                            + "through. Set a real Postgres connection string (Neon or Railway Postgres) as "
                            + "an environment variable — the application must never use localhost outside "
                            + "of local development.");
        }

        if (DANGEROUS_DDL.contains(ddlAuto)) {
            throw new IllegalStateException(
                    "Refusing startup: spring.jpa.hibernate.ddl-auto=" + ddlAuto
                            + " can destroy existing data.");
        }
        if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException(
                    "Refusing startup with an in-memory H2 datasource outside the test profile.");
        }
    }

    /** Resolves a property without letting an unresolved ${...} placeholder throw — returns "" instead. */
    private String resolveQuietly(ConfigurableEnvironment environment, String key) {
        try {
            String value = environment.getProperty(key, "");
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    /** Extracts just the host from a JDBC URL for logging — never logs credentials or the full URL. */
    private String safeHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return "";
        try {
            String withoutPrefix = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
            URI uri = URI.create(withoutPrefix);
            return uri.getHost() != null ? uri.getHost() : "";
        } catch (Exception e) {
            return "unparseable";
        }
    }
}
