package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.Notification;
import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.SubscriptionStatus;
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

    /** A resolved trial window for a new tenant — {@code hasTrial() == false} means no trial should be granted at all (plan.trialDays == 0 or trial disabled). */
    public record TrialWindow(LocalDateTime startedAt, LocalDateTime endsAt, boolean hasTrial) {
        public static TrialWindow none() {
            return new TrialWindow(null, null, false);
        }
    }

    /**
     * The single source of truth for "how long should this new tenant's trial
     * last": reads the plan's own isTrialEnabled + trialDays, never a
     * hardcoded constant and never a plan-name/code comparison (see class
     * Javadoc on SubscriptionPlan.isTrialEnabled). Every trial-creation call
     * site (registration, phone signup, Super Admin manual agency creation,
     * legacy-tenant backfill) must go through this so a Super Admin's edit to
     * a plan's Trial Days actually controls real trial duration going
     * forward — without touching any tenant whose trial already started.
     */
    public TrialWindow beginTrial(SubscriptionPlan plan, LocalDateTime now) {
        if (plan == null || !Boolean.TRUE.equals(plan.getIsTrialEnabled())
                || plan.getTrialDays() == null || plan.getTrialDays() <= 0) {
            return TrialWindow.none();
        }
        return new TrialWindow(now, now.plusDays(plan.getTrialDays()), true);
    }

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

        if (trialPlan && tenant.getStatus() == SubscriptionStatus.TRIAL && tenant.isTrialExpired()) {
            tenant.setStatus(SubscriptionStatus.TRIAL_EXPIRED);
            tenant.setSubscriptionActive(false);
            changed = true;
        }
        if (!trialPlan && tenant.getStatus() == SubscriptionStatus.TRIAL) {
            tenant.setStatus(SubscriptionStatus.ACTIVE);
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
        if (!trialPlan && tenant.getTrialStartedAt() != null) {
            tenant.setTrialStartedAt(null);
            changed = true;
        }
        if (!trialPlan && tenant.getTrialEndsAt() != null) {
            tenant.setTrialEndsAt(null);
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
        LocalDateTime now = LocalDateTime.now();

        tenant.setPlanName(plan.getName());
        tenant.setSubscriptionActive(true);
        tenant.setStatus(SubscriptionStatus.ACTIVE);
        tenant.setTrialStartDate(null);
        tenant.setTrialEndDate(null);
        tenant.setTrialStartedAt(null);
        tenant.setTrialEndsAt(null);
        tenant.setSubscriptionEndDate(today.plusMonths(Math.max(1, months)));
        tenant.setCurrentPeriodStart(now);
        tenant.setCurrentPeriodEnd(now.plusMonths(Math.max(1, months)));
        tenant.setSuspendedAt(null);
        tenant.setGracePeriodEnd(null);
        tenant.setGraceStartedNotifiedAt(null);
        tenant.setGraceSuspensionWarningNotifiedAt(null);
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

        SubscriptionStatus currentStatus = tenant.getStatus();
        if (currentStatus == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException("Subscription is already cancelled.");
        }
        if (currentStatus == SubscriptionStatus.CANCEL_AT_PERIOD_END) {
            throw new IllegalStateException("Cancellation is already scheduled.");
        }
        if (currentStatus == SubscriptionStatus.TRIAL) {
            throw new IllegalStateException("Trial subscriptions cannot be self-cancelled.");
        }

        LocalDateTime effectiveAt = tenant.getSubscriptionEndDate() != null
                ? tenant.getSubscriptionEndDate().atTime(23, 59, 59)
                : LocalDateTime.now().plusDays(1).withHour(23).withMinute(59).withSecond(59);

        tenant.setStatus(SubscriptionStatus.CANCEL_AT_PERIOD_END);
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

        if (tenant.getStatus() != SubscriptionStatus.CANCEL_AT_PERIOD_END) {
            throw new IllegalStateException("No pending cancellation to undo.");
        }

        tenant.setStatus(SubscriptionStatus.ACTIVE);
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

    /**
     * A payment failure — sets GRACE_PERIOD and the exact grace deadline
     * (never "end of calendar day"). Access remains fully usable throughout
     * (see {@link Tenant#isSubscriptionValid()}). No email is sent from here —
     * that is the scheduler's job (see the new RenewalAndGraceJob), so a
     * retried/duplicate webhook delivery can't double-send the notice.
     */
    @Transactional
    public Tenant startGracePeriod(Tenant tenant, SubscriptionPlan plan) {
        int graceDays = plan != null && plan.getGracePeriodDays() != null ? plan.getGracePeriodDays() : 3;
        LocalDateTime now = LocalDateTime.now();

        tenant.setStatus(SubscriptionStatus.GRACE_PERIOD);
        tenant.setGracePeriodEnd(now.plusDays(graceDays));
        tenant.setGraceStartedNotifiedAt(null);
        tenant.setGraceSuspensionWarningNotifiedAt(null);
        Tenant saved = tenantRepository.save(tenant);

        auditLogRepository.save(AuditLog.builder()
                .action("SUBSCRIPTION_GRACE_PERIOD_STARTED")
                .entityType("TENANT")
                .entityId(saved.getId())
                .tenantId(saved.getId())
                .description("Payment failed — grace period started, ends " + saved.getGracePeriodEnd())
                .performedBy(currentUsername())
                .isSuccess(true)
                .build());

        log.info("[SUBSCRIPTION] Grace period started for tenant [{}], ends [{}]", saved.getId(), saved.getGracePeriodEnd());
        return saved;
    }

    /**
     * Reactivates a tenant back to ACTIVE with a fresh billing period — the
     * single shared path for both "paid during grace" and "paid after
     * suspension" (per spec: these two triggers must not diverge).
     */
    @Transactional
    public Tenant reactivate(Tenant tenant, LocalDateTime periodStart, LocalDateTime periodEnd) {
        tenant.setStatus(SubscriptionStatus.ACTIVE);
        tenant.setSuspendedAt(null);
        tenant.setGracePeriodEnd(null);
        tenant.setGraceStartedNotifiedAt(null);
        tenant.setGraceSuspensionWarningNotifiedAt(null);
        tenant.setCurrentPeriodStart(periodStart);
        tenant.setCurrentPeriodEnd(periodEnd);
        tenant.setSubscriptionEndDate(periodEnd != null ? periodEnd.toLocalDate() : null);
        tenant.setSubscriptionActive(true);
        Tenant saved = tenantRepository.save(tenant);

        auditLogRepository.save(AuditLog.builder()
                .action("SUBSCRIPTION_REACTIVATED")
                .entityType("TENANT")
                .entityId(saved.getId())
                .tenantId(saved.getId())
                .description("Subscription reactivated, period end " + periodEnd)
                .performedBy(currentUsername())
                .isSuccess(true)
                .build());

        platformEmailService.sendAccountReactivated(saved);

        log.info("[SUBSCRIPTION] Tenant [{}] reactivated, period end [{}]", saved.getId(), periodEnd);
        return saved;
    }

    /**
     * The single source of truth for granting a new trial at signup — reuses
     * {@link #beginTrial(SubscriptionPlan, LocalDateTime)} for the actual day-count
     * math. Mutates the given (not-yet-persisted or already-persisted) tenant
     * in place; the caller is responsible for saving it.
     */
    public Tenant assignTrial(Tenant tenant, SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        TrialWindow trial = beginTrial(plan, now);
        tenant.setPlanName("Trial");
        tenant.setStatus(trial.hasTrial() ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE);
        tenant.setTrialStartDate(trial.hasTrial() ? trial.startedAt().toLocalDate() : null);
        tenant.setTrialEndDate(trial.hasTrial() ? trial.endsAt().toLocalDate() : null);
        tenant.setTrialStartedAt(trial.startedAt());
        tenant.setTrialEndsAt(trial.endsAt());
        return tenant;
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
