package com.carrental.repository;

import com.carrental.entity.AiMigrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMigrationLogRepository extends JpaRepository<AiMigrationLog, Long> {
}
