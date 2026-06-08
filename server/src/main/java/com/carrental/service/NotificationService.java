package com.carrental.service;

import com.carrental.entity.Notification;
import com.carrental.repository.NotificationRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseService sseService;

    @Transactional
    public Notification createNotification(String title, String message, Notification.NotificationType type,
                                            Long contractId, Long tenantId) {
        Notification notification = Notification.builder()
                .title(title)
                .message(message)
                .type(type)
                .contractId(contractId)
                .tenantId(tenantId)
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        sseService.broadcastToTenant(tenantId, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadForCurrentTenant() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findByTenantIdAndReadFalseOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecentForCurrentTenant(int limit) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForCurrentTenant() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.countByTenantIdAndReadFalse(tenantId);
    }

    @Transactional
    public void markAsRead(Long id) {
        Notification notification = notificationRepository.findByIdAndTenantId(
                        id, TenantContext.getCurrentTenantId())
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Notification> unread = notificationRepository.findByTenantIdAndReadFalseOrderByCreatedAtDesc(tenantId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
