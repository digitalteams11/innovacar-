package com.carrental.repository;

import com.carrental.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    List<EmailLog> findTop100ByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
