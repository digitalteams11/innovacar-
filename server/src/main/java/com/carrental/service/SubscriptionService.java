package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.Notification;
import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.AuditLogRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service to manage tenant subscriptions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final TenantRepository tenantRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final PlatformEmailService platformEmailService;

    @Transactional
    public Tenant repairSubscriptionState(Tenant tenant, SubscriptionPlan plan) {
        String planCode = planCode(plan, tenant.getPlanName());
        boolean trialPlan = "TRIAL".equals(planCode);
        boolean changed = false;

        // A deliberate Super-Admin block/suspend/deactivate is never something
        // to "repair" back to active — this status-repair logic exists only to
        // fix tenants whose subscriptionActive flag drifted out of sync with a
        // paid plan, not to second-guess an intentional enforcement action.
        if (tenant.isAccountBlocked()) {
            return tenant;
        }

        if (trialPlan && "TRIAL".equalsIgnoreCase(tenant.getStatus())
                && tenant.getTrialEndDate() != null
                && LocalDate.now().isAfter(tenant.getTrialEndDate())) {
            tenant.setStatus("EXPIRED");
            tenant.setSubscriptionActive(false);
            changed = true;
        }
        if (!trialPlan && "TRIAL".equalsIgnoreCase(tenant.getStatus())) {
            tenant.setStatus("ACTIVE");
            changed = true;
        }
        if (!trialPlan && tenant.getTrialStartDate() != null) {
            tenant.setTrialStartDate(null);
            changed = true;
        }
        if (!trialPlan && tenant.getTrialEndDate() != null) {
            tenant.setTrialEndDate(null);
            changed = true;
        }
        if (!trialPlan && !tenant.isSubscriptionActive()) {
            tenant.setSubscriptionActive(true);
            changed = true;
        }
        if (!trialPlan && tenant.getSubscriptionEndDate() == null) {
            tenant.setSubscriptionEndDate(LocalDate.now().plusMonths(1));
            changed = true;
        }

        return changed ? tenantRepository.save(tenant) : tenant;
    }

    @Transactional
    public Tenant activatePaidPlan(Tenant tenant, SubscriptionPlan plan, int months) {
        if (plan == null || "TRIAL".equals(planCode(plan, null))) {
            throw new IllegalArgumentException("A paid subscription plan is required");
        }

        String previousPlan = tenant.getPlanName() == null ? "Trial" : tenant.getPlanName();
        LocalDate today = LocalDate.now();

        tenant.setPlanName(plan.getName());
        tenant.setSubscriptionActive(true);
        tenant.setStatus("ACTIVE");
        tenant.setTrialStartDate(null);
        tenant.setTrialEndDate(null);
        tenant.setSubscriptionEndDate(today.plusMonths(Math.max(1, months)));
        tenant.setMaxVehicles(plan.getMaxVehicles());
        tenant.setMaxEmployees(plan.getMaxEmployees());
        tenant.setMaxGpsDevices(plan.getMaxGpsDevices());
        tenant.setMaxReservations(plan.getMaxReservations());
        tenant.setStorageLimitMb(plan.getStorageLimitMb());
        Tenant saved = tenantRepository.save(tenant);

        auditLogRepository.save(AuditLog.builder()
                .action("SUBSCRIPTION_UPGRADED")
                .entityType("TENANT")
                .entityId(saved.getId())
                .tenantId(saved.getId())
                .description("Subscription upgraded from " + previousPlan + " to " + plan.getName())
                .performedBy(currentUsername())
                .isSuccess(true)
                .build());
        notificationService.createNotification(
                plan.getName() + " plan activated successfully",
                "Your " + plan.getName() + " subscription is now active.",
                Notification.NotificationType.SUCCESS,
                null,
                saved.getId());

        log.info("Subscription upgraded for tenant [{}] from [{}] to [{}]", saved.getId(), previousPlan, plan.getName());
        return saved;
    }

    /**
     * Activates a tenant's subscription.
     */
    @Transactional
    public void activateSubscription() {
        Tenant tenant = getTenant();
        tenant.setSubscriptionActive(true);
        tenantRepository.save(tenant);
        log.info("Subscription activated for tenant [{}]", tenant.getId());
    }

    /**
     * Extends a tenant's subscription by a given number of days.
     * Also automatically activates it if it was inactive.
     */
    @Transactional
    public void extendSubscription(int daysToAdd) {
        Tenant tenant = getTenant();
        
        LocalDate currentDate = tenant.getSubscriptionEndDate();
        if (currentDate == null || currentDate.isBefore(LocalDate.now())) {
            currentDate = LocalDate.now();
        }
        
        tenant.setSubscriptionEndDate(currentDate.plusDays(daysToAdd));
        tenant.setSubscriptionActive(true);
        
        tenantRepository.save(tenant);
        log.info("Subscription for tenant [{}] extended to {}", tenant.getId(), tenant.getSubscriptionEndDate());
    }

    /**
     * Schedules end-of-period cancellation. Does NOT cancel immediately.
     * Status transitions to CANCEL_SCHEDULED; access continues until cancelEffectiveAt.
     */
    @Transactional
    public Tenant scheduleCancellation(Long tenantId, String reason, String feedback, User caller) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        String currentStatus = tenant.getStatus() != null ? tenant.getStatus().toUpperCase() : "";
        if ("CANCELLED".equals(currentStatus)) {
            throw new IllegalStateException("Subscription is already cancelled.");
        }
        if ("CANCEL_SCHEDULED".equals(currentStatus)) {
            throw new IllegalStateException("Cancellation is already scheduled.");
        }
        if ("TRIAL".equals(currentStatus)) {
            throw new IllegalStateException("Trial subscriptions cannot be self-cancelled.");
        }

        LocalDateTime effectiveAt = tenant.getSubscriptionEndDate() != null
                ? tenant.getSubscriptionEndDate().atTime(23, 59, 59)
                : LocalDateTime.now().plusDays(1).withHour(23).withMinute(59).withSecond(59);

        tenant.setStatus("CANCEL_SCHEDULED");
        tenant.setCancelRequestedAt(LocalDateTime.now());
        tenant.setCancelEffectiveAt(effectiveAt);
        tenant.setCancellationReason(StringUtils.hasText(reason) ? reason : "NOT_SPECIFIED");
        tenant.setCancellationFeedback(StringUtils.hasText(feedback) ? feedback.substring(0, Math.min(feedback.length(), 1000)) : null);
        Tenant saved = tenantRepository.save(tenant);

        String performedBy = caller != null ? caller.getEmail() : currentUsername();
        auditLogRepository.save(AuditLog.builder()
                .action("SUBSCRIPTION_CANCELLATION_SCHEDULED")
                .entityType("TENANT")
                .entityId(saved.getId())
                .tenantId(saved.getId())
                .description("Cancellation scheduled. Effective: " + effectiveAt + ". Reason: " + reason)
                .performedBy(performedBy)
                .isSuccess(true)
                .build());
        notificationService.createNotification(
                "Subscription Cancellation Scheduled",
                "Your subscription remains active until " + effectiveAt.toLocalDate() + ". You can undo this before then.",
                Notification.NotificationType.WARNING,
                null,
                saved.getId());

        platformEmailService.sendSubscriptionCancellationScheduled(
                saved.getId(), saved.getEmail(), saved.getName(), effectiveAt);

        log.info("[SUBSCRIPTION] Cancellation scheduled for tenant [{}] effective [{}] by [{}]",
                saved.getId(), effectiveAt, performedBy);
        return saved;
    }

    /**
     * Undoes a scheduled cancellation. Restores status to ACTIVE and clears all
     * cancellation fields. Only valid when status == CANCEL_SCHEDULED.
     */
    @Transactional
    public Tenant undoCancellation(Long tenantId, User caller) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (!"CANCEL_SCHEDULED".equalsIgnoreCase(tenant.getStatus())) {
            throw new IllegalStateException("No pending cancellation to undo.");
        }

        tenant.setStatus("ACTIVE");
        tenant.setCancelRequestedAt(null);
        tenant.setCancelEffectiveAt(null);
        tenant.setCancellationReason(null);
        tenant.setCancellationFeedback(null);
        Tenant saved = tenantRepository.save(tenant);

        String performedBy = caller != null ? caller.getEmail() : currentUsername();
        auditLogRepository.save(AuditLog.builder()
                .action("SUBSCRIPTION_CANCELLATION_UNDONE")
                .entityType("TENANT")
                .entityId(saved.getId())
                .tenantId(saved.getId())
                .description("Cancellation undone by " + performedBy)
                .performedBy(performedBy)
                .isSuccess(true)
                .build());
        notificationService.createNotification(
                "Cancellation Reversed",
                "Your subscription cancellation has been undone. Your plan will renew as scheduled.",
                Notification.NotificationType.SUCCESS,
                null,
                saved.getId());

        platformEmailService.sendSubscriptionCancellationUndone(
                saved.getId(), saved.getEmail(), saved.getName());

        log.info("[SUBSCRIPTION] Cancellation undone for tenant [{}] by [{}]", saved.getId(), performedBy);
        return saved;
    }

    private Tenant getTenant() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
    }

    private String planCode(SubscriptionPlan plan, String planName) {
        String value = plan != null && plan.getCode() != null ? plan.getCode() : planName;
        return value == null ? "TRIAL" : value.trim().toUpperCase();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
