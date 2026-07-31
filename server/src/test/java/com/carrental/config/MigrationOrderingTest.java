package com.carrental.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for two separate Railway startup incidents with the same
 * root cause: a migration merged to main with a version number that landed
 * <em>below</em> a version another branch had already gotten deployed and
 * applied first, so Flyway refused to migrate ("Detected resolved migration
 * not applied to database: N") and Spring Boot crashed during
 * flywayInitializer, before entityManagerFactory/userRepository ever
 * initialized.
 *
 * <ul>
 *   <li>V67/V68 (client_identity_normalization / vehicle_brand_model_split)
 *       were merged after production had already advanced past V69-V71 —
 *       renumbered to V72/V73.</li>
 *   <li>V82 (dashboard_layout_persistence) was merged ~6 minutes <em>before</em>
 *       V83 (see git history: V83 committed 2026-07-31T03:36, V82 committed
 *       2026-07-31T03:42) but reached a Railway deploy <em>after</em> V83 had
 *       already applied in production — renumbered to V84, the next version
 *       above everything already resolved in this repo.</li>
 * </ul>
 *
 * <p>No DB/Docker is required — this only checks filesystem invariants that
 * would have caught both incidents: every migration version is unique, and
 * every renumbered migration sits above the watermark that was already
 * applied in production at the time, so it can never again collide with an
 * already-applied version.
 */
class MigrationOrderingTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    /** The highest version production had actually applied when V67/V68 were merged (see class javadoc). */
    private static final int PRODUCTION_WATERMARK_AT_INCIDENT = 71;

    /** The highest version production had actually applied (V83) when V82/dashboard_layout_persistence collided with it. */
    private static final int PRODUCTION_WATERMARK_AT_DASHBOARD_INCIDENT = 83;

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

    @Test
    void dashboardLayoutPersistenceMigration_isAboveTheIncidentWatermark() {
        int version = versionOf(migrationDir(), "dashboard_layout_persistence");

        assertThat(version)
                .as("dashboard_layout_persistence must never regress to V82 or below — that version "
                        + "was already recorded as applied in production under a different migration (V83) "
                        + "by the time V82 was deployed")
                .isGreaterThan(PRODUCTION_WATERMARK_AT_DASHBOARD_INCIDENT);
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
