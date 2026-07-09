package com.carrental.service;

import com.carrental.entity.Notification;
import com.carrental.repository.NotificationRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEDUP_WINDOW_MINUTES = 5;
    private static final int MAX_PER_TENANT = 200;

    private final NotificationRepository notificationRepository;
    private final SseService sseService;

    // ── Legacy signature — backward-compatible with all existing callers ──────
    //
    // REQUIRES_NEW: notifications are a side effect, not part of the business
    // transaction that triggers them. Running in the caller's transaction means
    // a DB-level failure here (e.g. a check constraint rejecting an unknown
    // type) marks that transaction rollback-only even if the caller catches
    // the exception — the caller's later commit then throws
    // UnexpectedRollbackException. A dedicated transaction commits/rolls back
    // independently so a notification failure can never take down the
    // maintenance/contract/reservation/etc. write that triggered it.

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createNotification(String title, String message,
                                           Notification.NotificationType type,
                                           Long contractId, Long tenantId) {
        return createNotification(title, message, type,
                Notification.Severity.INFO,
                moduleForType(type),
                contractId != null ? "CONTRACTS" : null,
                contractId,
                contractId != null ? "/contracts/" + contractId : null,
                contractId, tenantId);
    }

    // ── Rich creation entry point ──────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createNotification(
            String title, String message,
            Notification.NotificationType type,
            Notification.Severity severity,
            Notification.Module module,
            String entityType, Long entityId,
            String actionUrl,
            Long contractId, Long tenantId) {

        if (tenantId == null) {
            log.warn("[NOTIFICATION] Skipped: tenantId is null, title={}", title);
            return null;
        }

        // Deduplication: skip if same type+entity already created in the last DEDUP_WINDOW_MINUTES
        if (entityId != null && type != null) {
            boolean exists = notificationRepository.existsByTenantIdAndTypeAndEntityIdAndCreatedAtAfter(
                    tenantId, type, entityId,
                    LocalDateTime.now().minusMinutes(DEDUP_WINDOW_MINUTES));
            if (exists) {
                log.debug("[NOTIFICATION] Dedup skip: type={} entityId={} tenantId={}", type, entityId, tenantId);
                return null;
            }
        }

        Notification notification = Notification.builder()
                .title(title)
                .message(message)
                .type(type != null ? type : Notification.NotificationType.INFORMATION)
                .severity(severity != null ? severity : Notification.Severity.INFO)
                .module(module)
                .entityType(entityType)
                .entityId(entityId)
                .actionUrl(actionUrl)
                .contractId(contractId)
                .tenantId(tenantId)
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        try {
            sseService.broadcastToTenant(tenantId, saved);
        } catch (Exception ex) {
            log.debug("[NOTIFICATION] SSE broadcast failed (non-critical): {}", ex.getMessage());
        }

        return saved;
    }

    // ── Convenience factory methods ────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification notifyMaintenance(String title, String message,
                                          Notification.NotificationType type,
                                          Notification.Severity severity,
                                          Long maintenanceId, Long tenantId) {
        return createNotification(title, message, type, severity,
                Notification.Module.MAINTENANCE,
                "MAINTENANCE", maintenanceId,
                "/maintenance",
                null, tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification notifyVehicle(String title, String message,
                                      Notification.NotificationType type,
                                      Notification.Severity severity,
                                      Long vehicleId, Long tenantId) {
        return createNotification(title, message, type, severity,
                Notification.Module.VEHICLES,
                "VEHICLE", vehicleId,
                "/vehicles",
                null, tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification notifyReservation(String title, String message,
                                          Notification.NotificationType type,
                                          Notification.Severity severity,
                                          Long reservationId, Long tenantId) {
        return createNotification(title, message, type, severity,
                Notification.Module.RESERVATIONS,
                "RESERVATION", reservationId,
                "/reservations",
                null, tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification notifyContract(String title, String message,
                                       Notification.NotificationType type,
                                       Notification.Severity severity,
                                       Long contractId, Long tenantId) {
        return createNotification(title, message, type, severity,
                Notification.Module.CONTRACTS,
                "CONTRACTS", contractId,
                contractId != null ? "/contracts/" + contractId : "/contracts",
                contractId, tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification notifyPayment(String title, String message,
                                      Notification.NotificationType type,
                                      Notification.Severity severity,
                                      Long paymentId, Long tenantId) {
        return createNotification(title, message, type, severity,
                Notification.Module.PAYMENTS,
                "PAYMENT", paymentId,
                "/payments",
                null, tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification notifySubscription(String title, String message,
                                           Notification.NotificationType type,
                                           Notification.Severity severity,
                                           Long tenantId) {
        return createNotification(title, message, type, severity,
                Notification.Module.SUBSCRIPTION,
                "SUBSCRIPTION", null,
                "/settings/subscription",
                null, tenantId);
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> getUnreadForCurrentTenant() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findByTenantIdAndReadFalseOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecentForCurrentTenant(int limit) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return notificationRepository.findByTenantIdOrderedBySeverityThenDate(
                tenantId, PageRequest.of(0, Math.min(limit, MAX_PER_TENANT)));
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForCurrentTenant() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return 0;
        return notificationRepository.countByTenantIdAndReadFalse(tenantId);
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    @Transactional
    public void markAsRead(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Notification notification = notificationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Notification> unread = notificationRepository.findByTenantIdAndReadFalseOrderByCreatedAtDesc(tenantId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(now);
        });
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public boolean delete(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return false;
        return notificationRepository.deleteByIdAndTenantId(id, tenantId) > 0;
    }

    @Transactional
    public int clearRead() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return 0;
        return notificationRepository.deleteAllByTenantIdAndReadTrue(tenantId);
    }

    // ── Response DTO ──────────────────────────────────────────────────────────

    public Map<String, Object> toResponseMap(Notification n) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("message", n.getMessage());
        m.put("type", n.getType().name());
        m.put("severity", n.getSeverity() != null ? n.getSeverity().name() : "INFO");
        m.put("module", n.getModule() != null ? n.getModule().name() : "");
        m.put("entityType", n.getEntityType() != null ? n.getEntityType() : "");
        m.put("entityId", n.getEntityId() != null ? n.getEntityId() : null);
        m.put("actionUrl", n.getActionUrl() != null ? n.getActionUrl() : "");
        m.put("contractId", n.getContractId());
        m.put("tenantId", n.getTenantId());
        m.put("read", n.getRead());
        m.put("createdAt", n.getCreatedAt().toString());
        return m;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Notification.Module moduleForType(Notification.NotificationType type) {
        if (type == null) return null;
        String name = type.name();
        if (name.startsWith("CONTRACT") || name.startsWith("QR") || name.startsWith("PDF")
                || name.startsWith("CLIENT_OPENED") || name.startsWith("CLIENT_SIGNED")) {
            return Notification.Module.CONTRACTS;
        }
        if (name.startsWith("RESERVATION")) return Notification.Module.RESERVATIONS;
        if (name.startsWith("VEHICLE") || name.startsWith("VEHICLE_BLOCKED")) return Notification.Module.VEHICLES;
        if (name.startsWith("MAINTENANCE")) return Notification.Module.MAINTENANCE;
        if (name.startsWith("PAYMENT") || name.startsWith("INVOICE") || name.startsWith("CLIENT_BALANCE")) {
            return Notification.Module.PAYMENTS;
        }
        if (name.startsWith("GPS")) return Notification.Module.GPS;
        if (name.startsWith("TRIAL") || name.startsWith("SUBSCRIPTION") || name.startsWith("PLAN")) {
            return Notification.Module.SUBSCRIPTION;
        }
        if (name.startsWith("EMPLOYEE")) return Notification.Module.EMPLOYEES;
        if (name.startsWith("LOGIN") || name.startsWith("PASSWORD") || name.startsWith("TWO_FACTOR")
                || name.startsWith("EMAIL_VERIFIED") || name.startsWith("SUSPICIOUS")) {
            return Notification.Module.SECURITY;
        }
        if (name.startsWith("BACKUP") || name.startsWith("SMTP") || name.startsWith("AI_")
                || name.startsWith("FEATURE")) {
            return Notification.Module.SYSTEM;
        }
        return null;
    }
}
