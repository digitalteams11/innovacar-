package com.carrental.service;

import com.carrental.entity.Notification;
import com.carrental.entity.Tenant;
import com.carrental.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that owns the free-trial lifecycle: expires trials whose
 * {@code trialEndDate} has passed, and sends the 7/3/1-day-before and
 * on-expiry reminder emails/notifications exactly once each (dedup is
 * tracked per-tenant via the trial_reminder_*_sent_at columns).
 *
 * <p>Runs hourly by default — frequent enough that the countdown/expiry never
 * lags a full day behind, but nowhere near "every few seconds". Only tenants
 * currently in TRIAL are loaded (not the whole tenants table), so this stays
 * cheap regardless of how many paid/expired/cancelled tenants exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialExpiryJob {

    private final TenantRepository tenantRepository;
    private final PlatformEmailService platformEmailService;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.subscription.trial-expiry.cron:0 0 * * * *}")
    public void processTrials() {
        List<Tenant> trialTenants = tenantRepository.findAllByStatusIgnoreCase("TRIAL");
        if (trialTenants.isEmpty()) {
            return;
        }

        long paidCount = tenantRepository.countByStatusIgnoreCase("ACTIVE");
        int expiredCount = 0;

        for (Tenant tenant : trialTenants) {
            try {
                expiredCount += processOne(tenant) ? 1 : 0;
            } catch (Exception e) {
                log.error("[TRIAL_EXPIRY] Failed to process tenant [{}]: {}", tenant.getId(), e.getMessage(), e);
            }
        }

        log.info("[TRIAL_EXPIRY] checked={} expired={} skippedPaid={}",
                trialTenants.size(), expiredCount, paidCount);
    }

    /** @return true if this tenant's trial was newly expired during this pass. */
    private boolean processOne(Tenant tenant) {
        if (tenant.getTrialEndDate() == null) {
            // Nothing to evaluate yet — ensureDefaultTrial()/registration backfills
            // this on the tenant's next status read; the job just skips it for now.
            return false;
        }

        if (tenant.isTrialExpired()) {
            tenant.setStatus("EXPIRED");
            tenant.setSubscriptionActive(false);
            boolean alreadyNotified = tenant.getTrialExpiredNotifiedAt() != null;
            tenant.setTrialExpiredNotifiedAt(LocalDateTime.now());
            tenantRepository.save(tenant);

            if (!alreadyNotified) {
                notificationService.createNotification(
                        "Your free trial has ended",
                        "Your Innovacar free trial has expired. Choose a plan to keep full access.",
                        Notification.NotificationType.WARNING,
                        null,
                        tenant.getId());
                platformEmailService.sendTrialExpired(tenant.getId(), tenant.getEmail(), tenant.getName());
            }
            return true;
        }

        long daysRemaining = tenant.trialDaysRemaining();
        if (daysRemaining == 7 && tenant.getTrialReminder7SentAt() == null) {
            sendReminder(tenant, 7);
            tenant.setTrialReminder7SentAt(LocalDateTime.now());
            tenantRepository.save(tenant);
        } else if (daysRemaining == 3 && tenant.getTrialReminder3SentAt() == null) {
            sendReminder(tenant, 3);
            tenant.setTrialReminder3SentAt(LocalDateTime.now());
            tenantRepository.save(tenant);
        } else if (daysRemaining == 1 && tenant.getTrialReminder1SentAt() == null) {
            sendReminder(tenant, 1);
            tenant.setTrialReminder1SentAt(LocalDateTime.now());
            tenantRepository.save(tenant);
        }
        return false;
    }

    private void sendReminder(Tenant tenant, long daysRemaining) {
        String dayWord = daysRemaining == 1 ? "day" : "days";
        notificationService.createNotification(
                "Your free trial ends in " + daysRemaining + " " + dayWord,
                "Choose a plan before your trial ends to avoid any interruption.",
                Notification.NotificationType.WARNING,
                null,
                tenant.getId());
        platformEmailService.sendTrialReminder(tenant.getId(), tenant.getEmail(), tenant.getName(), daysRemaining);
    }
}
