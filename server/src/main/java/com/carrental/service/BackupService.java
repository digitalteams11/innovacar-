package com.carrental.service;

import com.carrental.entity.BackupRecord;
import com.carrental.entity.User;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.BackupRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {
    private final BackupRecordRepository backupRepository;
    private final BackupCommandRunner commandRunner;
    private final DataSourceProperties dataSourceProperties;

    @Value("${app.backup.path:${app.storage.path:storage}/backups}")
    private String backupPath;

    @Value("${app.backup.pg-dump:pg_dump}")
    private String pgDumpExecutable;

    @Value("${app.backup.pg-restore:pg_restore}")
    private String pgRestoreExecutable;

    @Value("${app.backup.restore-enabled:false}")
    private boolean restoreEnabled;

    @Value("${app.backup.schedule.daily:0 0 2 * * *}")
    private String dailySchedule;

    @Value("${app.backup.schedule.weekly:0 30 2 * * SUN}")
    private String weeklySchedule;

    @Value("${app.backup.schedule.monthly:0 0 3 1 * *}")
    private String monthlySchedule;

    @Value("${app.backup.retention.daily-days:14}")
    private int dailyRetentionDays;

    @Value("${app.backup.retention.weekly-days:90}")
    private int weeklyRetentionDays;

    @Value("${app.backup.retention.monthly-days:730}")
    private int monthlyRetentionDays;

    public List<Map<String, Object>> list() {
        return backupRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toMap).toList();
    }

    public Map<String, Object> configuration() {
        return Map.of(
                "restoreEnabled", restoreEnabled,
                "daily", policy(dailySchedule, dailyRetentionDays),
                "weekly", policy(weeklySchedule, weeklyRetentionDays),
                "monthly", policy(monthlySchedule, monthlyRetentionDays));
    }

    public Map<String, Object> createManual() {
        User user = currentUser();
        return toMap(createBackup(BackupRecord.Type.MANUAL, user.getId(), user.getEmail()));
    }

    public Resource download(Long id) {
        BackupRecord record = completedRecord(id);
        Path path = verifiedPath(record);
        verifyChecksum(record, path);
        return new FileSystemResource(path);
    }

    public String downloadFileName(Long id) {
        return completedRecord(id).getFileName();
    }

    public Map<String, Object> restore(Long id, String confirmation) {
        if (!restoreEnabled) {
            throw new IllegalStateException("Database restore is disabled. Set BACKUP_RESTORE_ENABLED=true during a maintenance window.");
        }
        if (!Objects.equals(confirmation, "RESTORE " + id)) {
            throw new IllegalArgumentException("Confirmation must exactly match RESTORE " + id);
        }

        BackupRecord record = completedRecord(id);
        Path path = verifiedPath(record);
        verifyChecksum(record, path);
        User user = currentUser();
        createBackup(BackupRecord.Type.PRE_RESTORE, user.getId(), user.getEmail());

        record.setStatus(BackupRecord.Status.RESTORING);
        backupRepository.save(record);
        try {
            DatabaseTarget target = databaseTarget();
            List<String> command = new ArrayList<>(List.of(
                    pgRestoreExecutable,
                    "--clean",
                    "--if-exists",
                    "--no-owner",
                    "--no-privileges",
                    "--exit-on-error",
                    "--host", target.host(),
                    "--port", String.valueOf(target.port()),
                    "--username", target.username(),
                    "--dbname", target.database(),
                    path.toString()));
            BackupCommandRunner.Result result = commandRunner.run(
                    command,
                    passwordEnvironment(target.password()),
                    Duration.ofMinutes(60));
            if (result.exitCode() != 0) throw new IOException(safeOutput(result.output()));
            record.setStatus(BackupRecord.Status.RESTORED);
            record.setRestoredAt(LocalDateTime.now());
            record.setErrorMessage(null);
            return toMap(backupRepository.save(record));
        } catch (Exception ex) {
            record.setStatus(BackupRecord.Status.FAILED);
            record.setErrorMessage(limit(ex.getMessage(), 2000));
            backupRepository.save(record);
            throw new IllegalStateException("Database restore failed. Review the backup record before retrying.", ex);
        }
    }

    public void delete(Long id) {
        BackupRecord record = backupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Backup not found"));
        if (record.getStatus() == BackupRecord.Status.RUNNING || record.getStatus() == BackupRecord.Status.RESTORING) {
            throw new IllegalStateException("A running backup cannot be deleted");
        }
        if (record.getFilePath() != null) {
            try {
                Path path = Paths.get(record.getFilePath()).toAbsolutePath().normalize();
                ensureInsideBackupDirectory(path);
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to remove backup file", ex);
            }
        }
        backupRepository.delete(record);
    }

    @Scheduled(cron = "${app.backup.schedule.daily:0 0 2 * * *}")
    public void dailyBackup() {
        createScheduledIfMissing(BackupRecord.Type.DAILY, LocalDate.now().atStartOfDay());
        cleanupRetention();
    }

    @Scheduled(cron = "${app.backup.schedule.weekly:0 30 2 * * SUN}")
    public void weeklyBackup() {
        createScheduledIfMissing(BackupRecord.Type.WEEKLY, LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay());
    }

    @Scheduled(cron = "${app.backup.schedule.monthly:0 0 3 1 * *}")
    public void monthlyBackup() {
        createScheduledIfMissing(BackupRecord.Type.MONTHLY, LocalDate.now().withDayOfMonth(1).atStartOfDay());
    }

    private void createScheduledIfMissing(BackupRecord.Type type, LocalDateTime periodStart) {
        if (backupRepository.findFirstByTypeAndCreatedAtAfterOrderByCreatedAtDesc(type, periodStart).isPresent()) return;
        try {
            createBackup(type, null, "system");
        } catch (RuntimeException ex) {
            log.error("Scheduled {} backup failed", type, ex);
        }
    }

    private BackupRecord createBackup(BackupRecord.Type type, Long createdById, String createdBy) {
        BackupRecord record = backupRepository.save(BackupRecord.builder()
                .type(type)
                .status(BackupRecord.Status.RUNNING)
                .createdById(createdById)
                .createdBy(createdBy)
                .build());
        Path temporary = null;
        try {
            Path directory = backupDirectory();
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            String fileName = "rentcar-" + type.name().toLowerCase(Locale.ROOT) + "-" + timestamp + "-" + record.getId() + ".dump";
            Path destination = directory.resolve(fileName).normalize();
            ensureInsideBackupDirectory(destination);
            temporary = directory.resolve(fileName + ".partial");

            DatabaseTarget target = databaseTarget();
            List<String> command = List.of(
                    pgDumpExecutable,
                    "--format=custom",
                    "--compress=6",
                    "--no-owner",
                    "--no-privileges",
                    "--file", temporary.toString(),
                    "--host", target.host(),
                    "--port", String.valueOf(target.port()),
                    "--username", target.username(),
                    target.database());
            BackupCommandRunner.Result result = commandRunner.run(
                    command,
                    passwordEnvironment(target.password()),
                    Duration.ofMinutes(30));
            if (result.exitCode() != 0) throw new IOException(safeOutput(result.output()));
            if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                throw new IOException("pg_dump did not create a valid backup file");
            }
            moveCompletedBackup(temporary, destination);

            record.setFileName(fileName);
            record.setFilePath(destination.toString());
            record.setSizeBytes(Files.size(destination));
            record.setSha256(sha256(destination));
            record.setStatus(BackupRecord.Status.COMPLETED);
            record.setCompletedAt(LocalDateTime.now());
            record.setErrorMessage(null);
            return backupRepository.save(record);
        } catch (Exception ex) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
            record.setStatus(BackupRecord.Status.FAILED);
            record.setCompletedAt(LocalDateTime.now());
            record.setErrorMessage(limit(ex.getMessage(), 2000));
            backupRepository.save(record);
            throw new IllegalStateException("Database backup failed", ex);
        }
    }

    private void cleanupRetention() {
        cleanupType(BackupRecord.Type.DAILY, dailyRetentionDays);
        cleanupType(BackupRecord.Type.WEEKLY, weeklyRetentionDays);
        cleanupType(BackupRecord.Type.MONTHLY, monthlyRetentionDays);
    }

    private void cleanupType(BackupRecord.Type type, int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        backupRepository.findAllByStatusAndCreatedAtBefore(BackupRecord.Status.COMPLETED, cutoff).stream()
                .filter(record -> record.getType() == type)
                .forEach(record -> {
                    try {
                        if (record.getFilePath() != null) {
                            Path path = Paths.get(record.getFilePath()).toAbsolutePath().normalize();
                            ensureInsideBackupDirectory(path);
                            Files.deleteIfExists(path);
                        }
                        backupRepository.delete(record);
                    } catch (RuntimeException | IOException ex) {
                        log.warn("Unable to expire backup record {}", record.getId(), ex);
                    }
                });
    }

    private BackupRecord completedRecord(Long id) {
        return backupRepository.findById(id)
                .filter(record -> record.getStatus() == BackupRecord.Status.COMPLETED
                        || record.getStatus() == BackupRecord.Status.RESTORED)
                .orElseThrow(() -> new ResourceNotFoundException("Completed backup not found"));
    }

    private Path verifiedPath(BackupRecord record) {
        if (record.getFilePath() == null) throw new IllegalStateException("Backup file path is missing");
        Path path = Paths.get(record.getFilePath()).toAbsolutePath().normalize();
        ensureInsideBackupDirectory(path);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Backup file is missing");
        return path;
    }

    private Path backupDirectory() {
        try {
            Path directory = Paths.get(backupPath).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            return directory;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize backup storage", ex);
        }
    }

    private void ensureInsideBackupDirectory(Path path) {
        Path directory = Paths.get(backupPath).toAbsolutePath().normalize();
        if (!path.startsWith(directory)) throw new IllegalStateException("Invalid backup file path");
    }

    private void moveCompletedBackup(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void verifyChecksum(BackupRecord record, Path path) {
        if (record.getSha256() == null || !record.getSha256().equals(sha256(path))) {
            throw new IllegalStateException("Backup checksum validation failed");
        }
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate backup checksum", ex);
        }
    }

    private DatabaseTarget databaseTarget() {
        String url = dataSourceProperties.determineUrl();
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("Backups require a PostgreSQL JDBC datasource");
        }
        URI uri = URI.create(url.substring("jdbc:".length()));
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (database.isBlank()) throw new IllegalStateException("Database name is missing from datasource URL");
        return new DatabaseTarget(
                uri.getHost() == null ? "localhost" : uri.getHost(),
                uri.getPort() < 0 ? 5432 : uri.getPort(),
                database,
                dataSourceProperties.determineUsername(),
                dataSourceProperties.determinePassword());
    }

    private Map<String, String> passwordEnvironment(String password) {
        return password == null ? Map.of() : Map.of("PGPASSWORD", password);
    }

    private Map<String, Object> toMap(BackupRecord record) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", record.getId());
        value.put("type", record.getType());
        value.put("status", record.getStatus());
        value.put("fileName", record.getFileName());
        value.put("sizeBytes", record.getSizeBytes());
        value.put("sha256", record.getSha256());
        value.put("createdBy", record.getCreatedBy());
        value.put("errorMessage", record.getErrorMessage());
        value.put("createdAt", record.getCreatedAt());
        value.put("completedAt", record.getCompletedAt());
        value.put("restoredAt", record.getRestoredAt());
        return value;
    }

    private Map<String, Object> policy(String cron, int retentionDays) {
        return Map.of("cron", cron, "retentionDays", retentionDays);
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user;
        throw new IllegalStateException("Authenticated user not found");
    }

    private String safeOutput(String output) {
        String value = output == null || output.isBlank() ? "PostgreSQL command failed" : output.trim();
        return limit(value, 1000);
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record DatabaseTarget(String host, int port, String database, String username, String password) {}
}
