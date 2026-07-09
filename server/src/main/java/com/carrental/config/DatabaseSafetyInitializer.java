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
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", "none")
                .trim().toLowerCase(Locale.ROOT);
        boolean testProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("test"::equalsIgnoreCase);

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
