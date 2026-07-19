package com.carrental.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the Railway startup incident where flyway.repair() ran
 * unconditionally before every flyway.migrate() call, on every deploy, forever.
 * Repair must only run when schema history actually contains a FAILED migration,
 * and a blocked/failing migrate() must never be swallowed — it has to propagate
 * so Spring Boot startup fails loudly instead of the app coming up against an
 * unknown schema state.
 */
class FlywayConfigTest {

    private final FlywayMigrationStrategy strategy = new FlywayConfig().flywayMigrationStrategy();

    private Flyway mockFlyway(MigrationState... historyStates) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        MigrationInfo[] history = new MigrationInfo[historyStates.length];
        for (int i = 0; i < historyStates.length; i++) {
            MigrationInfo mi = mock(MigrationInfo.class);
            when(mi.getState()).thenReturn(historyStates[i]);
            when(mi.getVersion()).thenReturn(MigrationVersion.fromVersion(String.valueOf(60 + i)));
            when(mi.getDescription()).thenReturn("test migration " + i);
            history[i] = mi;
        }
        when(infoService.all()).thenReturn(history);
        when(infoService.pending()).thenReturn(new MigrationInfo[0]);
        when(flyway.info()).thenReturn(infoService);
        return flyway;
    }

    @Test
    void noFailedMigrations_neverCallsRepair() {
        Flyway flyway = mockFlyway(MigrationState.SUCCESS, MigrationState.SUCCESS);

        strategy.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    void failedMigrationInHistory_callsRepairBeforeMigrate() {
        Flyway flyway = mockFlyway(MigrationState.SUCCESS, MigrationState.FAILED);

        strategy.migrate(flyway);

        InOrder order = inOrder(flyway);
        order.verify(flyway).repair();
        order.verify(flyway).migrate();
    }



    @Test
    void migrateThrows_propagatesRatherThanSwallowing() {
        Flyway flyway = mockFlyway(MigrationState.SUCCESS);
        RuntimeException blocked = new RuntimeException("canceling statement due to lock timeout");
        doThrow(blocked).when(flyway).migrate();

        assertThatThrownBy(() -> strategy.migrate(flyway))
                .isSameAs(blocked);
    }

    @Test
    void infoCheckFails_doesNotRepairBlindly_migrateStillRuns() {
        Flyway flyway = mock(Flyway.class);
        // Every call to flyway.info() fails (e.g. connection issue) — neither the
        // failed-migration check nor the pending-count logging may propagate this;
        // migrate() must still be attempted regardless.
        when(flyway.info()).thenThrow(new RuntimeException("db unreachable"));

        strategy.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }
}
