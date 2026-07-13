package com.carrental.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public class DatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Set<String> DANGEROUS_DDL = Set.of("create", "create-drop", "drop");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String url = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", "none")
                .trim().toLowerCase(Locale.ROOT);
        boolean testProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("test"::equalsIgnoreCase);

        String profile = String.join(",", environment.getActiveProfiles());
        if (profile.isBlank()) {
            profile = environment.getProperty("spring.profiles.active", "default");
        }
        System.out.println("[DB_CONFIG_DEBUG] profile=" + profile
                + " urlPresent=" + !url.isBlank()
                + " usernamePresent=" + !username.isBlank()
                + " passwordPresent=" + !password.isBlank());

        if (!testProfile && password.isBlank()) {
            throw new IllegalStateException(
                    "DATABASE_PASSWORD_MISSING: spring.datasource.password is empty. "
                            + "Set the SPRING_DATASOURCE_PASSWORD environment variable to your local "
                            + "Postgres password before running, e.g. (PowerShell):\n"
                            + "  $env:SPRING_DATASOURCE_PASSWORD=\"<your-local-postgres-password>\"\n"
                            + "  mvn spring-boot:run\n"
                            + "See server/.env.example for all local dev environment variables.");
        }

        if (!testProfile && DANGEROUS_DDL.contains(ddlAuto)) {
            throw new IllegalStateException(
                    "Refusing startup: spring.jpa.hibernate.ddl-auto=" + ddlAuto
                            + " can destroy existing data.");
        }
        if (!testProfile && url.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException(
                    "Refusing startup with an in-memory H2 datasource outside the test profile.");
        }
    }
}
