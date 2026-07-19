package com.carrental.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Controls exactly how Flyway migrates on startup.
 *
 * <p>Previously this unconditionally ran {@code flyway.repair()} before every
 * single {@code flyway.migrate()} call, on every deploy, forever. Flyway's own
 * docs are explicit that {@code repair()} is a maintenance action for a known-bad
 * schema history (e.g. after manually fixing a migration that failed), not a
 * routine startup step — running it every time silently masks real problems
 * (a genuinely corrupted history stops looking any different from a healthy
 * one) and adds an extra unnecessary database round-trip to every deploy.
 *
 * <p>Now: {@code repair()} only runs when {@code flyway.info()} actually shows
 * a FAILED migration row — i.e. exactly the case it exists for (recovering
 * from a previous failed attempt) — and every migration run logs a clear
 * begin/complete/failed line with duration, so a hang or failure is always
 * visible and attributable to a specific version instead of a silent gap in
 * the startup log.
 */
@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            boolean hasFailedMigration = false;
            try {
                hasFailedMigration = Arrays.stream(flyway.info().all())
                        .anyMatch(mi -> mi.getState() == MigrationState.FAILED);
            } catch (Exception e) {
                // Can't tell — err on the side of NOT repairing blindly. A genuine
                // problem here will simply surface as a normal migrate() failure below.
                log.warn("[FLYWAY_INFO_CHECK_FAILED] Could not inspect schema history before migrating: {}",
                        e.getMessage());
            }

            if (hasFailedMigration) {
                log.warn("[FLYWAY_REPAIR] A previously failed migration was found in flyway_schema_history — "
                        + "running flyway.repair() once before migrating so the corrected script can be retried.");
                flyway.repair();
            }

            int pendingCount = -1;
            String next = "unknown";
            try {
                MigrationInfo[] pending = flyway.info().pending();
                pendingCount = pending.length;
                next = pending.length > 0 ? pending[0].getVersion() + " - " + pending[0].getDescription() : "none";
            } catch (Exception e) {
                log.warn("[FLYWAY_INFO_CHECK_FAILED] Could not list pending migrations before migrating: {}",
                        e.getMessage());
            }
            log.info("[FLYWAY_MIGRATION_BEGIN] pendingCount={} next={}", pendingCount, next);

            long startNanos = System.nanoTime();
            try {
                flyway.migrate();
                log.info("[FLYWAY_MIGRATION_COMPLETE] durationMs={}", elapsedMs(startNanos));
            } catch (Exception e) {
                // Never swallowed — a failed/blocked migration must stop startup loudly,
                // not leave the app running against an unknown schema state.
                log.error("[FLYWAY_MIGRATION_FAILED] durationMs={} exceptionClass={} message={}",
                        elapsedMs(startNanos), e.getClass().getName(), e.getMessage(), e);
                throw e;
            }
        };
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
