package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.Notification;
import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.SubscriptionStatus;
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
    @Mock private PlatformEmailService platformEmailService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void repairSubscriptionState_convertsEnterpriseTrialMismatchToActive() {
        Tenant tenant = paidTenantMarkedAsTrial();
        SubscriptionPlan enterprise = enterprisePlan();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        Tenant result = subscriptionService.repairSubscriptionState(tenant, enterprise);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
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
                .status(SubscriptionStatus.TRIAL)
                .subscriptionActive(true)
                .trialStartDate(LocalDate.now().minusMonths(2))
                .trialEndDate(LocalDate.now().minusDays(1))
                .build();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        Tenant result = subscriptionService.repairSubscriptionState(tenant, null);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_EXPIRED);
        assertThat(result.isSubscriptionActive()).isFalse();
    }

    @Test
    void repairSubscriptionState_leavesActivePaidSubscriberUntouched() {
        Tenant tenant = Tenant.builder()
                .id(3L)
                .name("Agency")
                .email("agency@test.com")
                .planName("Standard")
                .status(SubscriptionStatus.ACTIVE)
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusMonths(1))
                .build();
        SubscriptionPlan standard = SubscriptionPlan.builder().id(9L).name("Standard").code("standard").build();

        Tenant result = subscriptionService.repairSubscriptionState(tenant, standard);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
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
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
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
                .status(SubscriptionStatus.TRIAL)
                .build();

        // Super Admin later changes the plan to 1 day — beginTrial is only ever
        // called again for a *new* tenant; an already-started tenant's stored
        // trialEndsAt must never be recomputed just because the plan changed.
        originalPlan.setTrialDays(1);

        assertThat(agencyA.getTrialEndsAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 0, 0));
    }

    // ── New subscription-simplification coverage (Trial + Innovacar Complete state machine) ──

    // Behavior 2: a paid checkout can never target the TRIAL plan itself.
    @Test
    void activatePaidPlan_rejectsTrialPlan() {
        Tenant tenant = paidTenantMarkedAsTrial();
        SubscriptionPlan trial = trialPlan(14);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.activatePaidPlan(tenant, trial, 1));

        verify(tenantRepository, never()).save(any());
    }

    // Behavior 1: signup's assignTrial() must produce the exact plan.trialDays delta
    // (not "end of calendar day"), through the real call site used at registration.
    @Test
    void assignTrial_producesExactTrialDaysDelta_notCalendarDayApproximation() {
        SubscriptionPlan plan = trialPlan(1);
        Tenant tenant = Tenant.builder().id(10L).name("New Agency").email("new@test.com").build();

        Tenant result = subscriptionService.assignTrial(tenant, plan);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(result.getPlanName()).isEqualTo("Trial");
        assertThat(result.getTrialStartedAt()).isNotNull();
        assertThat(result.getTrialEndsAt()).isNotNull();
        assertThat(Duration.between(result.getTrialStartedAt(), result.getTrialEndsAt()))
                .isEqualTo(Duration.ofDays(1));
        assertThat(result.getTrialEndsAt()).isEqualTo(result.getTrialStartedAt().plusDays(1));
    }

    // Behavior 4: startGracePeriod with an explicit gracePeriodDays=3 plan.
    @Test
    void startGracePeriod_withExplicitThreeDayPlan_setsExactDeadlineAndClearsStaleDedup() {
        Tenant tenant = Tenant.builder()
                .id(20L).name("Agency").email("agency@test.com")
                .planName("Innovacar Complete")
                .status(SubscriptionStatus.ACTIVE)
                .subscriptionActive(true)
                // Stale dedup markers left over from a previous grace window.
                .graceStartedNotifiedAt(LocalDateTime.now().minusDays(30))
                .graceSuspensionWarningNotifiedAt(LocalDateTime.now().minusDays(29))
                .build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(2L).code("COMPLETE").gracePeriodDays(3).build();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        LocalDateTime before = LocalDateTime.now();
        Tenant result = subscriptionService.startGracePeriod(tenant, plan);
        LocalDateTime after = LocalDateTime.now();

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(result.getGracePeriodEnd()).isBetween(before.plusDays(3), after.plusDays(3));
        assertThat(result.getGraceStartedNotifiedAt()).isNull();
        assertThat(result.getGraceSuspensionWarningNotifiedAt()).isNull();

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("SUBSCRIPTION_GRACE_PERIOD_STARTED");
    }

    // Behavior 4: null plan (or null gracePeriodDays on the plan) falls back to exactly 3 days.
    @Test
    void startGracePeriod_withNullPlan_fallsBackToThreeDayDefault() {
        Tenant tenant = Tenant.builder()
                .id(21L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.ACTIVE).subscriptionActive(true)
                .build();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        LocalDateTime before = LocalDateTime.now();
        Tenant result = subscriptionService.startGracePeriod(tenant, null);
        LocalDateTime after = LocalDateTime.now();

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(result.getGracePeriodEnd()).isBetween(before.plusDays(3), after.plusDays(3));
    }

    // Behavior 5: reactivate() from GRACE_PERIOD converges on ACTIVE with everything cleared/set.
    @Test
    void reactivate_fromGracePeriod_convergesOnActiveWithPeriodSetAndDedupCleared() {
        Tenant tenant = Tenant.builder()
                .id(30L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().plusDays(1))
                .graceStartedNotifiedAt(LocalDateTime.now().minusDays(2))
                .graceSuspensionWarningNotifiedAt(LocalDateTime.now().minusHours(6))
                .subscriptionActive(true)
                .build();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        LocalDateTime periodStart = LocalDateTime.of(2026, 8, 5, 10, 0);
        LocalDateTime periodEnd = periodStart.plusMonths(1);
        Tenant result = subscriptionService.reactivate(tenant, periodStart, periodEnd);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getSuspendedAt()).isNull();
        assertThat(result.getGracePeriodEnd()).isNull();
        assertThat(result.getGraceStartedNotifiedAt()).isNull();
        assertThat(result.getGraceSuspensionWarningNotifiedAt()).isNull();
        assertThat(result.getCurrentPeriodStart()).isEqualTo(periodStart);
        assertThat(result.getCurrentPeriodEnd()).isEqualTo(periodEnd);
        assertThat(result.getSubscriptionEndDate()).isEqualTo(periodEnd.toLocalDate());
        assertThat(result.isSubscriptionActive()).isTrue();
        verify(platformEmailService).sendAccountReactivated(result);
    }

    // Behavior 5: reactivate() from SUSPENDED converges on the exact same result shape.
    @Test
    void reactivate_fromSuspended_convergesOnSameResultAsFromGracePeriod() {
        Tenant tenant = Tenant.builder()
                .id(31L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.SUSPENDED)
                .suspendedAt(LocalDateTime.now().minusDays(2))
                .subscriptionActive(false)
                .build();
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        LocalDateTime periodStart = LocalDateTime.of(2026, 8, 5, 10, 0);
        LocalDateTime periodEnd = periodStart.plusMonths(1);
        Tenant result = subscriptionService.reactivate(tenant, periodStart, periodEnd);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getSuspendedAt()).isNull();
        assertThat(result.getGracePeriodEnd()).isNull();
        assertThat(result.getCurrentPeriodStart()).isEqualTo(periodStart);
        assertThat(result.getCurrentPeriodEnd()).isEqualTo(periodEnd);
        assertThat(result.getSubscriptionEndDate()).isEqualTo(periodEnd.toLocalDate());
        assertThat(result.isSubscriptionActive()).isTrue();
    }

    // Behavior 15: reactivate()/startGracePeriod() never silently produce a fresh TRIAL status,
    // regardless of the tenant's prior terminal state.
    @Test
    void reactivateAndStartGracePeriod_neverProduceTrialStatus() {
        Tenant suspended = Tenant.builder().id(40L).name("A").email("a@test.com")
                .status(SubscriptionStatus.SUSPENDED).build();
        when(tenantRepository.save(suspended)).thenReturn(suspended);
        Tenant reactivated = subscriptionService.reactivate(suspended, LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
        assertThat(reactivated.getStatus()).isNotEqualTo(SubscriptionStatus.TRIAL);
        assertThat(reactivated.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        Tenant cancelled = Tenant.builder().id(41L).name("B").email("b@test.com")
                .status(SubscriptionStatus.CANCELLED).build();
        when(tenantRepository.save(cancelled)).thenReturn(cancelled);
        Tenant grace = subscriptionService.startGracePeriod(cancelled, null);
        assertThat(grace.getStatus()).isNotEqualTo(SubscriptionStatus.TRIAL);
        assertThat(grace.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
    }

    // Behavior 3: exact-second boundary for trial expiry — one second past trialEndsAt is
    // expired, one second before is not. Distinct from the existing hour-granularity test above.
    @Test
    void isTrialExpired_exactSecondBoundary_notJustSameDayApproximation() {
        LocalDateTime now = LocalDateTime.now();

        Tenant justExpired = Tenant.builder().id(50L).name("A").email("a@test.com")
                .trialStartedAt(now.minusDays(1).minusSeconds(1))
                .trialEndsAt(now.minusSeconds(1))
                .build();
        assertThat(justExpired.isTrialExpired()).isTrue();

        Tenant notYetExpired = Tenant.builder().id(51L).name("B").email("b@test.com")
                .trialStartedAt(now.minusDays(1).plusSeconds(1))
                .trialEndsAt(now.plusSeconds(1))
                .build();
        assertThat(notYetExpired.isTrialExpired()).isFalse();
    }

    // Behavior 12: SubscriptionService only ever depends on Tenant/AuditLog-related
    // collaborators — never a Vehicle/Client/Contract/Reservation repository — so none of
    // its status-transition methods (startGracePeriod/reactivate/etc.) can touch that data.
    @Test
    void subscriptionService_hasNoDependencyOnFleetOrBookingData() {
        for (java.lang.reflect.Field field : SubscriptionService.class.getDeclaredFields()) {
            String typeName = field.getType().getSimpleName();
            assertThat(typeName)
                    .as("SubscriptionService field '%s' of type %s must not reach into fleet/booking data", field.getName(), typeName)
                    .doesNotContainIgnoringCase("Vehicle")
                    .doesNotContainIgnoringCase("Client")
                    .doesNotContainIgnoringCase("Contract")
                    .doesNotContainIgnoringCase("Reservation");
        }
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
                .status(SubscriptionStatus.TRIAL)
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
