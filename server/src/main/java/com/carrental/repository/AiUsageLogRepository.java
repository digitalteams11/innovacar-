package com.carrental.repository;

import com.carrental.entity.AiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    Page<AiUsageLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AiUsageLog> findAllByAgencyIdOrderByCreatedAtDesc(Long agencyId, Pageable pageable);

    Page<AiUsageLog> findAllByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    long countByAgencyIdAndCreatedAtAfter(Long agencyId, LocalDateTime after);

    long countByCreatedAtAfter(LocalDateTime after);

    long countByStatusAndCreatedAtAfter(String status, LocalDateTime after);

    long countByProviderId(Long providerId);
}
