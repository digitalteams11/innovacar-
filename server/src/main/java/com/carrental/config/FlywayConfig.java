package com.carrental.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Automatically repairs Flyway's schema history before each migration run.
 * This clears any failed migration records so corrected SQL files can be retried.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayRepairStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
