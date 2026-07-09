package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.Tenant;
import com.carrental.repository.AuditLogRepository;
import com.carrental.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily job that finalises scheduled subscription cancellations once the
 * billing period has ended (status CANCEL_SCHEDULED, cancelEffectiveAt <= now).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleJob {

    private final TenantRepository      tenantRepository;
    private final AuditLogRepository    auditLogRepository;
    private final PlatformEmailService  platformEmailService;

    @Scheduled(cron = "${app.subscription.lifecycle.cron:0 5 3 * * *}")
    public void processScheduledCancellations() {
        LocalDateTime now = LocalDateTime.now();
        List<Tenant> due = tenantRepository
                .findAllByStatusIgnoreCaseAndCancelEffectiveAtBefore("CANCEL_SCHEDULED", now);

        if (due.isEmpty()) {
            return;
        }
        log.info("[LIFECYCLE] Processing {} scheduled cancellation(s)", due.size());

        for (Tenant tenant : due) {
            try {
                tenant.setStatus("CANCELLED");
                tenant.setSubscriptionActive(false);
                tenant.setCancelledAt(now);
                tenantRepository.save(tenant);

                auditLogRepository.save(AuditLog.builder()
                        .action("SUBSCRIPTION_CANCELLED_FINAL")
                        .entityType("TENANT")
                        .entityId(tenant.getId())
                        .tenantId(tenant.getId())
                        .description("Subscription cancelled by lifecycle job at end of billing period.")
                        .performedBy("system")
                        .isSuccess(true)
                        .build());

                platformEmailService.sendSubscriptionCancelledFinal(
                        tenant.getId(), tenant.getEmail(), tenant.getName());

                log.info("[LIFECYCLE] Cancelled tenant [{}] ({})", tenant.getId(), tenant.getName());
            } catch (Exception e) {
                log.error("[LIFECYCLE] Failed to cancel tenant [{}]: {}", tenant.getId(), e.getMessage(), e);
            }
        }
    }
}
