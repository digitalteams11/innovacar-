package com.carrental.repository;

import com.carrental.entity.RoleAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAuditLogRepository extends JpaRepository<RoleAuditLog, Long> {
    Page<RoleAuditLog> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    Page<RoleAuditLog> findAllByTargetUserIdOrderByCreatedAtDesc(Long targetUserId, Pageable pageable);
}
