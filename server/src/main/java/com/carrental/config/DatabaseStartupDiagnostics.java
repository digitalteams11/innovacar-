package com.carrental.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseStartupDiagnostics implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        long startNanos = System.nanoTime();
        log.info("[STARTUP_STEP_BEGIN] DatabaseStartupDiagnostics");
        try {
            runInternal();
            log.info("[STARTUP_STEP_OK] DatabaseStartupDiagnostics durationMs={}",
                    (System.nanoTime() - startNanos) / 1_000_000);
        } catch (Exception e) {
            // Deliberately NOT rethrown — see DataInitializer for the full explanation.
            // This is pure diagnostic logging (row counts); it must never be able to
            // crash the app it's only trying to describe.
            log.error("[STARTUP_STEP_FAILED] DatabaseStartupDiagnostics exceptionClass={} message={}",
                    e.getClass().getName(), e.getMessage());
        }
    }

    private void runInternal() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            log.info("Database safety diagnostics: profiles={}, url={}, database={}, schema={}, ddl-auto={}",
                    Arrays.toString(environment.getActiveProfiles()),
                    metadata.getURL(),
                    connection.getCatalog(),
                    connection.getSchema(),
                    environment.getProperty("spring.jpa.hibernate.ddl-auto", "none"));
        }

        log.info("Business row counts: clients={}, vehicles={}, contracts={}, reservations={}, payments={}",
                count("clients"), count("vehicles"), count("contracts"),
                count("reservations"), count("payments"));
    }

    private Long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }
}
