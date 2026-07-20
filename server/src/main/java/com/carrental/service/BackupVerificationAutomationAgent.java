package com.carrental.service;

import com.carrental.entity.AutomationAlert;
import com.carrental.entity.BackupRecord;
import com.carrental.repository.BackupRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Automation Center's Backup Verification Agent — database backup execution itself is a
 * platform-level Super Admin responsibility (see BackupService), not something a tenant
 * triggers, so this agent runs once for the whole platform (tenant_id = null in
 * automation_runs), not once per tenant. It never runs pg_dump/pg_restore itself — it only
 * reads BackupRecord state that BackupService's own scheduled jobs already produced and
 * surfaces it as automation history + alerts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupVerificationAutomationAgent {

    private final BackupRecordRepository backupRecordRepository;
    private final AutomationRunRecorder automationRunRecorder;

    /** Daily backups are expected at least every 24h — alert once past 36h to allow for scheduling jitter. */
    private static final Duration STALE_THRESHOLD = Duration.ofHours(36);

    @Scheduled(cron = "${app.automation.backup-verification.cron:0 15 3 * * *}")
    public void verify() {
        LocalDateTime startedAt = automationRunRecorder.start();
        Optional<BackupRecord> lastCompleted = backupRecordRepository
                .findFirstByStatusOrderByCreatedAtDesc(BackupRecord.Status.COMPLETED);

        if (lastCompleted.isEmpty()) {
            automationRunRecorder.raiseAlert(null, AutomationAgentKeys.BACKUP_VERIFICATION,
                    AutomationAlert.Severity.CRITICAL, "No successful backup on record",
                    "No completed database backup has ever been recorded.");
            automationRunRecorder.recordFailure(null, AutomationAgentKeys.BACKUP_VERIFICATION, startedAt,
                    "NO_BACKUP_FOUND", "No completed backup exists.");
            return;
        }

        BackupRecord record = lastCompleted.get();
        Duration age = Duration.between(record.getCompletedAt() != null ? record.getCompletedAt() : record.getCreatedAt(),
                LocalDateTime.now());
        boolean stale = age.compareTo(STALE_THRESHOLD) > 0;
        boolean sizeOk = record.getSizeBytes() != null && record.getSizeBytes() > 0;
        boolean checksumOk = record.getSha256() != null && !record.getSha256().isBlank();

        if (stale || !sizeOk || !checksumOk) {
            automationRunRecorder.raiseAlert(null, AutomationAgentKeys.BACKUP_VERIFICATION,
                    AutomationAlert.Severity.WARNING, "Backup verification issue",
                    "Last backup " + record.getFileName() + " — stale=" + stale + " sizeOk=" + sizeOk + " checksumOk=" + checksumOk);
        }

        String summary = "Last backup " + record.getFileName() + ", " + age.toHours() + "h ago, "
                + record.getSizeBytes() + " bytes.";
        automationRunRecorder.recordSuccess(null, AutomationAgentKeys.BACKUP_VERIFICATION, startedAt, summary);
    }
}
