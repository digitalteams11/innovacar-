package com.carrental.repository;

import com.carrental.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndTenantId(Long id, Long tenantId);

    List<Notification> findByTenantIdAndReadFalseOrderByCreatedAtDesc(Long tenantId);

    List<Notification> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.tenantId = :tenantId ORDER BY n.read ASC, n.severity DESC, n.createdAt DESC")
    List<Notification> findByTenantIdOrderedBySeverityThenDate(@Param("tenantId") Long tenantId, Pageable pageable);

    long countByTenantIdAndReadFalse(Long tenantId);

    boolean existsByTenantIdAndTitleAndCreatedAtAfter(Long tenantId, String title, LocalDateTime createdAt);

    boolean existsByTenantIdAndTypeAndEntityIdAndCreatedAtAfter(
            Long tenantId,
            Notification.NotificationType type,
            Long entityId,
            LocalDateTime createdAt);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.tenantId = :tenantId")
    int deleteByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.tenantId = :tenantId AND n.read = true")
    int deleteAllByTenantIdAndReadTrue(@Param("tenantId") Long tenantId);

    /** Deletes every notification linked to a contract — used by contract trash purge. */
    void deleteAllByContractId(Long contractId);
}
