package com.carrental.service;

import com.carrental.entity.BackupRecord;
import com.carrental.entity.User;
import com.carrental.repository.BackupRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {
    @Mock private BackupRecordRepository backupRepository;
    @Mock private BackupCommandRunner commandRunner;

    @TempDir
    Path backupDirectory;

    private BackupService service;

    @BeforeEach
    void setUp() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://db.internal:5433/rentcar");
        properties.setUsername("backup_user");
        properties.setPassword("secret");
        service = new BackupService(backupRepository, commandRunner, properties);
        ReflectionTestUtils.setField(service, "backupPath", backupDirectory.toString());
        ReflectionTestUtils.setField(service, "pgDumpExecutable", "pg_dump");
        ReflectionTestUtils.setField(service, "pgRestoreExecutable", "pg_restore");
        // @PostConstruct (checkCapability) never runs on a manually-`new`'d service —
        // simulate a successful startup probe so these tests exercise the actual
        // backup/restore logic rather than the "tool not installed" guard.
        ReflectionTestUtils.setField(service, "pgDumpAvailable", true);
        ReflectionTestUtils.setField(service, "pgRestoreAvailable", true);
        ReflectionTestUtils.setField(service, "restoreEnabled", false);
        ReflectionTestUtils.setField(service, "dailyRetentionDays", 14);
        ReflectionTestUtils.setField(service, "weeklyRetentionDays", 90);
        ReflectionTestUtils.setField(service, "monthlyRetentionDays", 730);

        User user = User.builder().id(7L).email("admin@rentcar.test").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsNativeBackupAndStoresIntegrityMetadata() throws Exception {
        when(backupRepository.save(any(BackupRecord.class))).thenAnswer(invocation -> {
            BackupRecord record = invocation.getArgument(0);
            if (record.getId() == null) record.setId(42L);
            return record;
        });
        when(commandRunner.run(anyList(), anyMap(), eq(Duration.ofMinutes(30))))
                .thenAnswer(invocation -> {
                    List<String> command = invocation.getArgument(0);
                    Path output = Path.of(command.get(command.indexOf("--file") + 1));
                    Files.writeString(output, "postgres custom backup");
                    assertThat(command).contains(
                            "--host", "db.internal",
                            "--port", "5433",
                            "--username", "backup_user",
                            "rentcar");
                    Map<String, String> environment = invocation.getArgument(1);
                    assertThat(environment).containsEntry("PGPASSWORD", "secret");
                    return new BackupCommandRunner.Result(0, "");
                });

        Map<String, Object> result = service.createManual();

        assertThat(result)
                .containsEntry("id", 42L)
                .containsEntry("type", BackupRecord.Type.MANUAL)
                .containsEntry("status", BackupRecord.Status.COMPLETED)
                .containsEntry("createdBy", "admin@rentcar.test");
        assertThat((Long) result.get("sizeBytes")).isPositive();
        assertThat((String) result.get("sha256")).hasSize(64);
        try (var files = Files.list(backupDirectory)) {
            assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
    }

    @Test
    void restoreRemainsLockedByDefault() {
        assertThatThrownBy(() -> service.restore(42L, "RESTORE 42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
        verifyNoInteractions(commandRunner);
    }

    @Test
    void missingPgDump_failsFastWithReadableMessage_neverAttemptsProcessBuilder() throws Exception {
        ReflectionTestUtils.setField(service, "pgDumpAvailable", false);
        when(backupRepository.save(any(BackupRecord.class))).thenAnswer(invocation -> {
            BackupRecord record = invocation.getArgument(0);
            if (record.getId() == null) record.setId(43L);
            return record;
        });

        assertThatThrownBy(() -> service.createManual())
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(commandRunner);
        var captor = org.mockito.ArgumentCaptor.forClass(BackupRecord.class);
        verify(backupRepository, atLeastOnce()).save(captor.capture());
        BackupRecord failed = captor.getAllValues().stream()
                .filter(r -> r.getStatus() == BackupRecord.Status.FAILED).findFirst().orElseThrow();
        assertThat(failed.getErrorMessage()).isEqualTo("PostgreSQL backup tool is not installed on the server.");
    }

    @Test
    void checkCapability_probesVersionAndMarksAvailable_neverThrows() throws Exception {
        BackupCommandRunner runner = mock(BackupCommandRunner.class);
        when(runner.run(eq(List.of("pg_dump", "--version")), anyMap(), any()))
                .thenReturn(new BackupCommandRunner.Result(0, "pg_dump (PostgreSQL) 16.4\n"));
        when(runner.run(eq(List.of("pg_restore", "--version")), anyMap(), any()))
                .thenReturn(new BackupCommandRunner.Result(1, ""));

        BackupService probed = new BackupService(backupRepository, runner, new DataSourceProperties());
        ReflectionTestUtils.setField(probed, "pgDumpExecutable", "pg_dump");
        ReflectionTestUtils.setField(probed, "pgRestoreExecutable", "pg_restore");

        probed.checkCapability();

        Map<String, Object> health = probed.health();
        assertThat(health).containsEntry("pgDumpAvailable", true);
        assertThat(health).containsEntry("pgDumpVersion", "pg_dump (PostgreSQL) 16.4");
        assertThat(health).containsEntry("pgRestoreAvailable", false);
        assertThat(health).containsEntry("backupReady", true);
    }

    @Test
    void checkCapability_executableNotFound_neverThrows_marksUnavailable() throws Exception {
        BackupCommandRunner runner = mock(BackupCommandRunner.class);
        when(runner.run(anyList(), anyMap(), any())).thenThrow(new java.io.IOException("Cannot run program \"pg_dump\": error=2, No such file or directory"));

        BackupService probed = new BackupService(backupRepository, runner, new DataSourceProperties());
        ReflectionTestUtils.setField(probed, "pgDumpExecutable", "pg_dump");
        ReflectionTestUtils.setField(probed, "pgRestoreExecutable", "pg_restore");

        probed.checkCapability();

        Map<String, Object> health = probed.health();
        assertThat(health).containsEntry("pgDumpAvailable", false);
        assertThat(health).containsEntry("backupReady", false);
    }
}
