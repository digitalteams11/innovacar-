package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.Notification;
import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.Tenant;
import com.carrental.repository.AuditLogRepository;
import com.carrental.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void repairSubscriptionState_convertsEnterpriseTrialMismatchToActive() {
        Tenant tenant = paidTenantMarkedAsTrial();
        SubscriptionPlan enterprise = enterprisePlan();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        Tenant result = subscriptionService.repairSubscriptionState(tenant, enterprise);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.isSubscriptionActive()).isTrue();
        assertThat(result.getTrialStartDate()).isNull();
        assertThat(result.getTrialEndDate()).isNull();
        assertThat(result.getSubscriptionEndDate()).isAfter(LocalDate.now());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void repairSubscriptionState_expiresTrialPastEndDateAndNeverGoesBack() {
        Tenant tenant = Tenant.builder()
                .id(2L)
                .name("Agency")
                .email("agency@test.com")
                .planName("Trial")
                .status("TRIAL")
                .subscriptionActive(true)
                .trialStartDate(LocalDate.now().minusMonths(2))
                .trialEndDate(LocalDate.now().minusDays(1))
                .build();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        Tenant result = subscriptionService.repairSubscriptionState(tenant, null);

        assertThat(result.getStatus()).isEqualTo("EXPIRED");
        assertThat(result.isSubscriptionActive()).isFalse();
    }

    @Test
    void repairSubscriptionState_leavesActivePaidSubscriberUntouched() {
        Tenant tenant = Tenant.builder()
                .id(3L)
                .name("Agency")
                .email("agency@test.com")
                .planName("Standard")
                .status("ACTIVE")
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusMonths(1))
                .build();
        SubscriptionPlan standard = SubscriptionPlan.builder().id(9L).name("Standard").code("standard").build();

        Tenant result = subscriptionService.repairSubscriptionState(tenant, standard);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.isSubscriptionActive()).isTrue();
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void activatePaidPlan_clearsTrialAndCreatesAuditAndNotification() {
        Tenant tenant = paidTenantMarkedAsTrial();
        tenant.setPlanName("Trial");
        SubscriptionPlan enterprise = enterprisePlan();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        Tenant result = subscriptionService.activatePaidPlan(tenant, enterprise, 1);

        assertThat(result.getPlanName()).isEqualTo("Enterprise");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.isSubscriptionActive()).isTrue();
        assertThat(result.getTrialStartDate()).isNull();
        assertThat(result.getTrialEndDate()).isNull();
        assertThat(result.getSubscriptionEndDate()).isEqualTo(LocalDate.now().plusMonths(1));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getDescription())
                .isEqualTo("Subscription upgraded from Trial to Enterprise");
        verify(notificationService).createNotification(
                eq("Enterprise plan activated successfully"),
                contains("Enterprise subscription is now active"),
                eq(Notification.NotificationType.SUCCESS),
                isNull(),
                eq(1L));
    }

    // Regression coverage for the production bug where Super Admin's "Trial
    // Days" field was fully persisted/editable but had zero real effect —
    // every trial-creation code path hardcoded exactly one calendar month
    // (Tenant.TRIAL_PERIOD_MONTHS), completely ignoring the plan's own
    // trialDays value. SubscriptionService#beginTrial is now the single
    // source of truth every trial-creation call site goes through.

    @Test
    void beginTrial_withTrialDaysOne_createsExactlyA24HourWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 15, 0, 0);
        SubscriptionPlan plan = trialPlan(1);

        SubscriptionService.TrialWindow trial = subscriptionService.beginTrial(plan, now);

        assertThat(trial.hasTrial()).isTrue();
        assertThat(trial.startedAt()).isEqualTo(now);
        assertThat(trial.endsAt()).isEqualTo(LocalDateTime.of(2026, 7, 30, 15, 0, 0));
        assertThat(Duration.between(trial.startedAt(), trial.endsAt())).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void beginTrial_withTrialDaysFourteen_createsExactlyFourteenDays() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 9, 30, 0);
        SubscriptionPlan plan = trialPlan(14);

        SubscriptionService.TrialWindow trial = subscriptionService.beginTrial(plan, now);

        assertThat(trial.hasTrial()).isTrue();
        assertThat(Duration.between(trial.startedAt(), trial.endsAt())).isEqualTo(Duration.ofDays(14));
        assertThat(trial.endsAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 9, 30, 0));
    }

    @Test
    void beginTrial_withTrialDaysZero_grantsNoTrial() {
        SubscriptionService.TrialWindow trial = subscriptionService.beginTrial(trialPlan(0), LocalDateTime.now());

        assertThat(trial.hasTrial()).isFalse();
        assertThat(trial.startedAt()).isNull();
        assertThat(trial.endsAt()).isNull();
    }

    @Test
    void beginTrial_whenTrialDisabledOnPlan_grantsNoTrialEvenWithPositiveTrialDays() {
        SubscriptionPlan plan = trialPlan(14);
        plan.setIsTrialEnabled(false);

        SubscriptionService.TrialWindow trial = subscriptionService.beginTrial(plan, LocalDateTime.now());

        assertThat(trial.hasTrial()).isFalse();
    }

    @Test
    void beginTrial_withNullPlan_grantsNoTrial() {
        assertThat(subscriptionService.beginTrial(null, LocalDateTime.now()).hasTrial()).isFalse();
    }

    @Test
    void tenantIsTrialExpired_usesExactTimestampNotJustCalendarDay() {
        LocalDateTime now = LocalDateTime.now();
        Tenant tenant = Tenant.builder().id(1L).name("A").email("a@test.com")
                .trialStartedAt(now.minusHours(23))
                .trialEndsAt(now.plusHours(1))
                .build();
        assertThat(tenant.isTrialExpired()).isFalse();
        assertThat(tenant.isInTrial()).isTrue();

        Tenant expiring = Tenant.builder().id(2L).name("B").email("b@test.com")
                .trialStartedAt(now.minusHours(25))
                .trialEndsAt(now.minusHours(1))
                .build();
        assertThat(expiring.isTrialExpired()).isTrue();
        assertThat(expiring.isInTrial()).isFalse();
    }

    @Test
    void editingPlanTrialDays_doesNotChangeAnAlreadyStartedTenantsTrialWindow() {
        // Agency A started when trialDays was 14.
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        SubscriptionPlan originalPlan = trialPlan(14);
        SubscriptionService.TrialWindow original = subscriptionService.beginTrial(originalPlan, start);
        Tenant agencyA = Tenant.builder().id(1L).name("Agency A").email("a@test.com")
                .trialStartedAt(original.startedAt())
                .trialEndsAt(original.endsAt())
                .status("TRIAL")
                .build();

        // Super Admin later changes the plan to 1 day — beginTrial is only ever
        // called again for a *new* tenant; an already-started tenant's stored
        // trialEndsAt must never be recomputed just because the plan changed.
        originalPlan.setTrialDays(1);

        assertThat(agencyA.getTrialEndsAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 0, 0));
    }

    private SubscriptionPlan trialPlan(int trialDays) {
        return SubscriptionPlan.builder()
                .id(1L).name("Trial").code("TRIAL")
                .trialDays(trialDays).isTrialEnabled(trialDays > 0)
                .build();
    }

    private Tenant paidTenantMarkedAsTrial() {
        return Tenant.builder()
                .id(1L)
                .name("Agency")
                .email("agency@test.com")
                .planName("Enterprise")
                .status("TRIAL")
                .subscriptionActive(false)
                .trialStartDate(LocalDate.now().minusDays(10))
                .trialEndDate(LocalDate.now().plusDays(50))
                .build();
    }

    private SubscriptionPlan enterprisePlan() {
        return SubscriptionPlan.builder()
                .id(5L)
                .name("Enterprise")
                .code("enterprise")
                .maxVehicles(9999)
                .maxEmployees(9999)
                .maxGpsDevices(9999)
                .maxReservations(99999)
                .storageLimitMb(1048576)
                .build();
    }
}
