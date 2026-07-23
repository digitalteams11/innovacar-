package com.carrental.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the Railway startup incident where V67/V68
 * (client_identity_normalization / vehicle_brand_model_split) were merged
 * after production's flyway_schema_history had already advanced past V69-V71,
 * so Flyway refused to migrate ("Detected resolved migration not applied to
 * database: 67/68") and Spring Boot crashed during flywayInitializer, before
 * entityManagerFactory/userRepository ever initialized. The fix renumbered
 * those two files to V72/V73 (see git history for
 * V72__client_identity_normalization.sql / V73__vehicle_brand_model_split.sql).
 *
 * <p>No DB/Docker is required — this only checks filesystem invariants that
 * would have caught the incident: every migration version is unique, and the
 * two renumbered migrations sit above the watermark that was already applied
 * in production at the time (V71), so they can never again collide with an
 * already-applied version.
 */
class MigrationOrderingTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    /** The highest version production had actually applied when V67/V68 were merged (see class javadoc). */
    private static final int PRODUCTION_WATERMARK_AT_INCIDENT = 71;

    private File migrationDir() {
        File dir = new File("src/main/resources/db/migration");
        assertThat(dir).as("Flyway migration directory").isDirectory();
        return dir;
    }

    @Test
    void everyMigrationVersionIsUnique() {
        Map<Integer, String> seen = new HashMap<>();
        for (File f : migrationDir().listFiles()) {
            Matcher m = VERSIONED_MIGRATION.matcher(f.getName());
            if (!m.matches()) continue;
            int version = Integer.parseInt(m.group(1));
            String previous = seen.put(version, f.getName());
            assertThat(previous)
                    .as("Migration version %d is used by both %s and %s — Flyway requires unique versions",
                            version, previous, f.getName())
                    .isNull();
        }
        assertThat(seen).isNotEmpty();
    }

    @Test
    void clientIdentityAndVehicleBrandModelMigrations_areAboveTheIncidentWatermark() {
        File dir = migrationDir();
        int clientIdentityVersion = versionOf(dir, "client_identity_normalization");
        int vehicleBrandModelVersion = versionOf(dir, "vehicle_brand_model_split");

        assertThat(clientIdentityVersion)
                .as("client_identity_normalization must never regress below the watermark that caused the incident")
                .isGreaterThan(PRODUCTION_WATERMARK_AT_INCIDENT);
        assertThat(vehicleBrandModelVersion)
                .as("vehicle_brand_model_split must never regress below the watermark that caused the incident")
                .isGreaterThan(PRODUCTION_WATERMARK_AT_INCIDENT);
    }

    private int versionOf(File dir, String descriptionContains) {
        for (File f : dir.listFiles()) {
            Matcher m = VERSIONED_MIGRATION.matcher(f.getName());
            if (m.matches() && f.getName().contains(descriptionContains)) {
                return Integer.parseInt(m.group(1));
            }
        }
        throw new AssertionError("No migration found for '" + descriptionContains + "' under " + dir);
    }
}
