package com.carrental.repository;

import com.carrental.entity.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {
    List<BackupRecord> findAllByOrderByCreatedAtDesc();
    Optional<BackupRecord> findFirstByTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            BackupRecord.Type type, LocalDateTime since);
    List<BackupRecord> findAllByStatusAndCreatedAtBefore(
            BackupRecord.Status status, LocalDateTime before);
}
