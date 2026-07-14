package com.carrental.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Railway's managed Postgres plugin (and some Neon connection strings) expose
 * the database as DATABASE_URL in the driver-agnostic "postgres://user:pass@host:port/db"
 * form. The pgJDBC driver requires "jdbc:postgresql://host:port/db" instead, so this
 * converts it into spring.datasource.* properties before they're resolved — but only
 * when SPRING_DATASOURCE_URL was not already set explicitly (explicit config always wins).
 * <p>
 * Runs as an {@link EnvironmentPostProcessor} (registered in META-INF/spring.factories)
 * so the converted values are in place before spring.datasource.url's "${SPRING_DATASOURCE_URL}"
 * placeholder in application.yml is resolved.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String explicitUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return; // explicit config always wins — never override it
        }

        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return; // nothing to convert
        }
        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            return; // not the raw driver-agnostic form (e.g. already jdbc:) — leave alone
        }

        try {
            URI uri = URI.create(databaseUrl);

            String username = "";
            String password = "";
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                int idx = userInfo.indexOf(':');
                if (idx >= 0) {
                    username = URLDecoder.decode(userInfo.substring(0, idx), StandardCharsets.UTF_8);
                    password = URLDecoder.decode(userInfo.substring(idx + 1), StandardCharsets.UTF_8);
                } else {
                    username = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
                }
            }

            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : 5432;
            String database = uri.getPath() != null ? uri.getPath().replaceFirst("^/", "") : "";
            String query = uri.getQuery();

            // Neon requires SSL; Railway's own Postgres accepts it too, so default to
            // "require" unless the DATABASE_URL already specifies a mode, or the
            // deployer explicitly overrides it via DATASOURCE_SSL_MODE (e.g. "disable"
            // for a private-network connection that doesn't terminate TLS).
            boolean hasSslMode = query != null && query.toLowerCase(java.util.Locale.ROOT).contains("sslmode=");
            String sslMode = environment.getProperty("DATASOURCE_SSL_MODE", "require");
            String effectiveQuery;
            if (hasSslMode) {
                effectiveQuery = query;
            } else if (query == null || query.isBlank()) {
                effectiveQuery = "sslmode=" + sslMode;
            } else {
                effectiveQuery = query + "&sslmode=" + sslMode;
            }

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database
                    + (effectiveQuery.isBlank() ? "" : "?" + effectiveQuery);

            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("spring.datasource.url", jdbcUrl);
            if (!username.isBlank()) converted.put("spring.datasource.username", username);
            if (!password.isBlank()) converted.put("spring.datasource.password", password);

            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrlConversion", converted));

            // Never log the raw DATABASE_URL (it contains the password) or the converted
            // password — only enough to confirm the conversion happened and where it points.
            System.out.println("[DB_URL_CONVERSION] Converted DATABASE_URL to JDBC form: host=" + host
                    + " port=" + port + " database=" + database + " sslmode=" + (hasSslMode ? "(from url)" : sslMode));
        } catch (Exception e) {
            // Deliberately omit e.getMessage() — URI parsing exceptions can echo the
            // offending input back, which would leak the embedded password into logs.
            System.err.println("[DB_URL_CONVERSION] Failed to parse DATABASE_URL: " + e.getClass().getSimpleName());
        }
    }
}
