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
import java.util.List;
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

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void scanAgencyRisk() {
        LocalDateTime now = LocalDateTime.now();
        List<UserSession> sessions = userSessionRepository.findAll();

        for (Tenant tenant : tenantRepository.findAll()) {
            if ("system@innovax.tech".equals(tenant.getEmail())) continue;

            if (tenant.getSubscriptionEndDate() != null) {
                long days = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getSubscriptionEndDate());
                if (days >= 0 && days <= 7) {
                    notifyOnce(tenant, "Subscription expires soon",
                            "Your subscription expires in " + days + " days. Renew now to avoid service interruption.",
                            Notification.NotificationType.SUBSCRIPTION_EXPIRES_SOON, now);
                }
            }

            Set<Long> userIds = userRepository.findAllByTenantId(tenant.getId()).stream()
                    .map(User::getId).collect(Collectors.toSet());
            LocalDateTime lastLogin = sessions.stream()
                    .filter(session -> userIds.contains(session.getUserId()))
                    .map(UserSession::getCreatedAt)
                    .filter(date -> date != null)
                    .max(LocalDateTime::compareTo)
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
        log.info("Customer success risk scan completed");
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
