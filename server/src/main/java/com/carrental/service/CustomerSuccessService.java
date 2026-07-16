package com.carrental.service;

import com.carrental.entity.Notification;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.entity.UserSession;
import com.carrental.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerSuccessService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    /**
     * A scheduled job must never terminate the process: the whole run is wrapped
     * so a single unexpected failure (a bad tenant row, a transient DB error, an
     * SMTP failure surfacing as a RuntimeException) logs one concise summary
     * instead of propagating. Each tenant is also isolated inside the loop so one
     * tenant's failure doesn't abort the scan for every other tenant.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void scanAgencyRisk() {
        try {
            runRiskScan();
        } catch (Exception ex) {
            log.error("[CUSTOMER_SUCCESS_SCAN] Aborted — exceptionClass={} message={}",
                    ex.getClass().getName(), ex.getMessage());
        }
    }

    @Transactional
    void runRiskScan() {
        LocalDateTime now = LocalDateTime.now();

        for (Tenant tenant : tenantRepository.findAll()) {
            if ("system@innovax.tech".equals(tenant.getEmail())) continue;
            try {
                scanTenantRisk(tenant, now);
            } catch (Exception ex) {
                log.error("[CUSTOMER_SUCCESS_SCAN] Skipped tenant [id={}] — exceptionClass={} message={}",
                        tenant.getId(), ex.getClass().getName(), ex.getMessage());
            }
        }
        log.info("Customer success risk scan completed");
    }

    private void scanTenantRisk(Tenant tenant, LocalDateTime now) {
        if (tenant.getSubscriptionEndDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getSubscriptionEndDate());
            if (days >= 0 && days <= 7) {
                notifyOnce(tenant, "Subscription expires soon",
                        "Your subscription expires in " + days + " days. Renew now to avoid service interruption.",
                        Notification.NotificationType.SUBSCRIPTION_EXPIRES_SOON, now);
            }
        }

        // Pushed down to the database (ORDER BY + LIMIT 1) instead of loading
        // every session row across every tenant into memory just to compute a
        // max() in application code — this previously loaded the entire
        // sessions table once per scheduled run regardless of tenant count.
        Set<Long> userIds = userRepository.findAllByTenantId(tenant.getId()).stream()
                .map(User::getId).collect(Collectors.toSet());
        LocalDateTime lastLogin = userIds.isEmpty() ? tenant.getCreatedAt()
                : userSessionRepository.findTopByUserIdInOrderByCreatedAtDesc(userIds)
                        .map(UserSession::getCreatedAt)
                        .orElse(tenant.getCreatedAt());
        if (lastLogin != null && lastLogin.isBefore(now.minusDays(30))) {
            notifyOnce(tenant, "Agency account inactive",
                    "No account login was detected for 30 days. Review your workflow or contact customer success.",
                    Notification.NotificationType.ACCOUNT_INACTIVE, now);
        }

        long activity = vehicleRepository.countByTenantId(tenant.getId())
                + reservationRepository.findAllByTenantId(tenant.getId()).size()
                + contractRepository.findAllByTenantId(tenant.getId()).size();
        if (activity < 3) {
            notifyOnce(tenant, "Usage below recommended level",
                    "Platform usage is below 20%. Add vehicles, create reservations, or review plan onboarding.",
                    Notification.NotificationType.LOW_USAGE, now);
        }
    }

    private void notifyOnce(Tenant tenant, String title, String message,
                            Notification.NotificationType type, LocalDateTime now) {
        if (notificationRepository.existsByTenantIdAndTitleAndCreatedAtAfter(
                tenant.getId(), title, now.minusDays(7))) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .tenantId(tenant.getId())
                .title(title)
                .message(message)
                .type(type)
                .read(false)
                .build());
        emailService.sendCustomerSuccessEmail(tenant.getEmail(), title, message);
    }
}
