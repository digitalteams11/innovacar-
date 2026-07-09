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
}
