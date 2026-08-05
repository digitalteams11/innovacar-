package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.SubscriptionStatus;
import com.carrental.entity.Tenant;
import com.carrental.repository.AuditLogRepository;
import com.carrental.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that owns the paid-renewal-reminder and grace-period/
 * suspension lifecycle — the paid-plan sibling of {@link TrialExpiryJob}.
 *
 * <p>Mirrors TrialExpiryJob's exact idiom: hourly cron, loads only the
 * tenants it needs (status=ACTIVE for renewal reminders, status=GRACE_PERIOD
 * for the grace lifecycle), one-time-send dedup via {@code *SentAt}/
 * {@code *NotifiedAt} columns on {@link Tenant}. No cross-instance locking —
 * matches the existing single-instance assumption of TrialExpiryJob/
 * SubscriptionLifecycleJob (a pre-existing gap, not solved fresh here).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalAndGraceJob {

    private final TenantRepository tenantRepository;
    private final AuditLogRepository auditLogRepository;
    private final PlatformEmailService platformEmailService;

    @Scheduled(cron = "${app.subscription.renewal-grace.cron:0 15 * * * *}")
    public void process() {
        processRenewalReminders();
        processGracePeriod();
    }

    /** ACTIVE tenants whose currentPeriodEnd is 5/3/1 day(s) out get one reminder each, exactly once. */
    private void processRenewalReminders() {
        List<Tenant> activeTenants = tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE);
        for (Tenant tenant : activeTenants) {
            try {
                processRenewalReminder(tenant);
            } catch (Exception e) {
                log.error("[RENEWAL_GRACE] Failed to process renewal reminder for tenant [{}]: {}", tenant.getId(), e.getMessage(), e);
            }
        }
    }

    private void processRenewalReminder(Tenant tenant) {
        if (tenant.getCurrentPeriodEnd() == null) return;

        long daysRemaining = Duration.between(LocalDateTime.now(), tenant.getCurrentPeriodEnd()).toDays();
        if (daysRemaining == 5 && tenant.getRenewalReminder5SentAt() == null) {
            platformEmailService.sendRenewalUpcoming(tenant, 5);
            tenant.setRenewalReminder5SentAt(LocalDateTime.now());
            tenantRepository.save(tenant);
        } else if (daysRemaining == 3 && tenant.getRenewalReminder3SentAt() == null) {
            platformEmailService.sendRenewalUpcoming(tenant, 3);
            tenant.setRenewalReminder3SentAt(LocalDateTime.now());
            tenantRepository.save(tenant);
        } else if (daysRemaining == 1 && tenant.getRenewalReminder1SentAt() == null) {
            platformEmailService.sendRenewalUpcoming(tenant, 1);
            tenant.setRenewalReminder1SentAt(LocalDateTime.now());
            tenantRepository.save(tenant);
        }
    }

    /** GRACE_PERIOD tenants: "grace started" once, "suspended tomorrow" once at <=24h out, then the actual suspension once gracePeriodEnd has passed. */
    private void processGracePeriod() {
        List<Tenant> graceTenants = tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD);
        for (Tenant tenant : graceTenants) {
            try {
                processOneGraceTenant(tenant);
            } catch (Exception e) {
                log.error("[RENEWAL_GRACE] Failed to process grace-period tenant [{}]: {}", tenant.getId(), e.getMessage(), e);
            }
        }
    }

    private void processOneGraceTenant(Tenant tenant) {
        LocalDateTime now = LocalDateTime.now();

        if (tenant.getGraceStartedNotifiedAt() == null) {
            platformEmailService.sendPaymentFailedGraceStarted(tenant);
            tenant.setGraceStartedNotifiedAt(now);
            tenantRepository.save(tenant);
        }

        if (tenant.getGracePeriodEnd() == null) return;

        boolean within24h = !tenant.getGracePeriodEnd().isAfter(now.plusHours(24));
        if (within24h && tenant.getGraceSuspensionWarningNotifiedAt() == null) {
            platformEmailService.sendSuspensionWarning(tenant);
            tenant.setGraceSuspensionWarningNotifiedAt(now);
            tenantRepository.save(tenant);
        }

        if (!tenant.getGracePeriodEnd().isAfter(now)) {
            tenant.setStatus(SubscriptionStatus.SUSPENDED);
            tenant.setSuspendedAt(now);
            tenantRepository.save(tenant);

            auditLogRepository.save(AuditLog.builder()
                    .action("SUBSCRIPTION_SUSPENDED")
                    .entityType("TENANT")
                    .entityId(tenant.getId())
                    .tenantId(tenant.getId())
                    .description("Grace period elapsed at " + tenant.getGracePeriodEnd() + " — tenant suspended.")
                    .performedBy("system")
                    .isSuccess(true)
                    .build());

            platformEmailService.sendAccountSuspended(tenant);

            log.info("[RENEWAL_GRACE] Tenant [{}] suspended — grace period elapsed at [{}]", tenant.getId(), tenant.getGracePeriodEnd());
        }
    }
}
