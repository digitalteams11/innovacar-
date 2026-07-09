package com.carrental.repository;

import com.carrental.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    List<NotificationRead> findByAdminUserId(Long adminUserId);

    boolean existsByAdminUserIdAndNotificationId(Long adminUserId, Long notificationId);
}
