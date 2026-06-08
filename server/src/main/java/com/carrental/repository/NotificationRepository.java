package com.carrental.repository;

import com.carrental.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndTenantId(Long id, Long tenantId);

    List<Notification> findByTenantIdAndReadFalseOrderByCreatedAtDesc(Long tenantId);

    List<Notification> findByTenantIdOrderByCreatedAtDesc(Long tenantId, org.springframework.data.domain.Pageable pageable);

    long countByTenantIdAndReadFalse(Long tenantId);

    boolean existsByTenantIdAndTitleAndCreatedAtAfter(Long tenantId, String title, LocalDateTime createdAt);
}
