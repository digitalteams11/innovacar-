package com.carrental.repository;

import com.carrental.entity.AiAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {
    Page<AiAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AiAuditLog> findAllByAgencyIdOrderByCreatedAtDesc(Long agencyId, Pageable pageable);

    long countByUserIdAndCreatedAtAfter(Long userId, java.time.LocalDateTime after);

    long countByAgencyIdAndCreatedAtAfter(Long agencyId, java.time.LocalDateTime after);

    long countByAgencyId(Long agencyId);

    void deleteAllByAgencyId(Long agencyId);
}
