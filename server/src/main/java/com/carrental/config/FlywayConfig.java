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
 *
 * <p>Startup order is deliberately log → migrate (which validates internally,
 * per {@code spring.flyway.validate-on-migrate=true}) → fail fast. Note this
 * does <em>not</em> call {@code flyway.validate()} as a separate step before
 * {@code migrate()}: standalone {@code validate()} treats a merely-{@code
 * PENDING} migration as a validation failure by default (it exists to check
 * "is the schema history internally consistent", not "is there work to do"),
 * which would reject completely normal startups that simply have a new
 * migration to run. {@code migrate()}'s own internal validation (via
 * {@code validate-on-migrate}) correctly excludes plain pending migrations
 * while still catching the real problems (checksum mismatches, out-of-order).
 *
 * <p>{@link org.flywaydb.core.api.MigrationInfoService#pending()} only counts
 * migrations that will actually run in the upcoming <em>ordered</em>
 * migrate() — a resolved-but-{@code OUT_OF_ORDER} migration (its version is
 * below the highest already-applied version, which is exactly what "Detected
 * resolved migration not applied to database: N" means) is invisible to
 * {@code pending()}, which is why that log line could previously read
 * {@code pendingCount=0 next=none} in the same startup where {@code migrate()}
 * then threw a validate exception a few lines later — a genuinely confusing,
 * contradictory-looking pair of log lines. {@link #logProblemResolvedMigrations}
 * scans every resolved migration's state up front and logs anything that
 * isn't a clean SUCCESS/PENDING/BASELINE by name, so an out-of-order,
 * missing, or future migration is named in the log the moment Flyway sees
 * it — never only discoverable from the exception several lines later.
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

            logProblemResolvedMigrations(flyway);

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
                // migrate() validates internally (spring.flyway.validate-on-migrate=true) — a
                // checksum mismatch or out-of-order migration surfaces here as a
                // FlywayValidateException, distinguishable from a real SQL execution failure
                // by exceptionClass alone, without needing a separate validate() call that
                // would incorrectly reject plain pending migrations (see class javadoc).
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

    /**
     * Names every resolved migration whose state isn't a clean SUCCESS/PENDING/BASELINE —
     * most importantly OUT_OF_ORDER, which is exactly what "Detected resolved migration not
     * applied to database: N" means and which {@code info().pending()} never surfaces (see
     * class javadoc). Purely diagnostic: never blocks or alters the migration itself.
     */
    private void logProblemResolvedMigrations(org.flywaydb.core.Flyway flyway) {
        try {
            MigrationInfo[] all = flyway.info().all();
            for (MigrationInfo mi : all) {
                MigrationState state = mi.getState();
                if (state == MigrationState.SUCCESS || state == MigrationState.PENDING
                        || state == MigrationState.BASELINE || state == MigrationState.BELOW_BASELINE) {
                    continue;
                }
                log.warn("[FLYWAY_PROBLEM_MIGRATION] version={} description={} state={} script={} — "
                        + "this migration will not run in the normal ordered migrate() and validate() "
                        + "will likely fail because of it.",
                        mi.getVersion(), mi.getDescription(), state, mi.getScript());
            }
        } catch (Exception e) {
            log.warn("[FLYWAY_INFO_CHECK_FAILED] Could not scan resolved migrations for problem states: {}",
                    e.getMessage());
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
