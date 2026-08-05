package com.carrental.service;

import com.carrental.entity.AuditLog;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the new grace-period/suspension and renewal-reminder scheduler
 * (RenewalAndGraceJob), the paid-plan sibling of TrialExpiryJob. None of
 * this behavior had any test coverage before this change — the production
 * class only existed, it was never exercised.
 */
@ExtendWith(MockitoExtension.class)
class RenewalAndGraceJobTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PlatformEmailService platformEmailService;

    @InjectMocks
    private RenewalAndGraceJob renewalAndGraceJob;

    // ── processGracePeriod: suspension timing (Behavior 9) ─────────────────────────

    @Test
    void gracePeriod_twoDaysFromDeadline_notSuspendedAndNoWarningYet() {
        Tenant tenant = Tenant.builder()
                .id(1L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().plusDays(2))
                .graceStartedNotifiedAt(LocalDateTime.now().minusHours(1)) // already sent, so isolate the warning check
                .build();
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of(tenant));

        renewalAndGraceJob.process();

        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(tenant.getSuspendedAt()).isNull();
        assertThat(tenant.getGraceSuspensionWarningNotifiedAt()).isNull();
        verify(platformEmailService, never()).sendSuspensionWarning(any());
        verify(platformEmailService, never()).sendAccountSuspended(any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void gracePeriod_twelveHoursFromDeadline_getsSuspensionWarningExactlyOnceAcrossTwoRuns() {
        Tenant tenant = Tenant.builder()
                .id(2L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().plusHours(12))
                .graceStartedNotifiedAt(LocalDateTime.now().minusDays(2))
                .build();
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of(tenant));

        renewalAndGraceJob.process();

        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(tenant.getGraceSuspensionWarningNotifiedAt()).isNotNull();
        verify(platformEmailService, times(1)).sendSuspensionWarning(tenant);

        // Running the job again (still 12h out — dedup marker now set) must not resend.
        renewalAndGraceJob.process();
        verify(platformEmailService, times(1)).sendSuspensionWarning(tenant);
    }

    @Test
    void gracePeriod_deadlineInThePast_getsSuspendedWithAuditLogAndEmail_exactlyOnce() {
        Tenant tenant = Tenant.builder()
                .id(3L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().minusHours(1))
                .graceStartedNotifiedAt(LocalDateTime.now().minusDays(4))
                .graceSuspensionWarningNotifiedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of(tenant));

        renewalAndGraceJob.process();

        assertThat(tenant.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(tenant.getSuspendedAt()).isNotNull();
        verify(platformEmailService, times(1)).sendAccountSuspended(tenant);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("SUBSCRIPTION_SUSPENDED");
        assertThat(captor.getValue().getTenantId()).isEqualTo(3L);

        // A second run against a tenant that's now SUSPENDED (not GRACE_PERIOD) is never fed
        // to processGracePeriod again — simulate the job re-running by re-querying only
        // GRACE_PERIOD tenants and confirming this tenant is no longer in that set.
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of());
        renewalAndGraceJob.process();
        verify(platformEmailService, times(1)).sendAccountSuspended(tenant);
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    void gracePeriod_sendsGraceStartedNoticeExactlyOnce() {
        Tenant tenant = Tenant.builder()
                .id(4L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().plusDays(3))
                .build();
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of(tenant));

        renewalAndGraceJob.process();
        assertThat(tenant.getGraceStartedNotifiedAt()).isNotNull();
        verify(platformEmailService, times(1)).sendPaymentFailedGraceStarted(tenant);

        renewalAndGraceJob.process();
        verify(platformEmailService, times(1)).sendPaymentFailedGraceStarted(tenant);
    }

    // Behavior 14 (tenant isolation): processing a batch never leaks state between tenants —
    // one tenant's suspension must not affect an unrelated tenant still safely inside its window.
    @Test
    void gracePeriod_batchProcessing_onlyMutatesTheTenantThatIsDue() {
        Tenant dueForSuspension = Tenant.builder()
                .id(5L).name("Due Agency").email("due@test.com")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().minusMinutes(5))
                .graceStartedNotifiedAt(LocalDateTime.now().minusDays(4))
                .graceSuspensionWarningNotifiedAt(LocalDateTime.now().minusDays(1))
                .build();
        Tenant safeTenant = Tenant.builder()
                .id(6L).name("Safe Agency").email("safe@test.com")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().plusDays(2))
                .graceStartedNotifiedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD))
                .thenReturn(List.of(dueForSuspension, safeTenant));

        renewalAndGraceJob.process();

        assertThat(dueForSuspension.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(safeTenant.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(safeTenant.getSuspendedAt()).isNull();
        verify(platformEmailService, never()).sendAccountSuspended(safeTenant);
        verify(platformEmailService).sendAccountSuspended(dueForSuspension);
    }

    // Behavior 15: the job only ever queries ACTIVE/GRACE_PERIOD tenants — a TRIAL_EXPIRED,
    // SUSPENDED, or CANCELLED tenant is never loaded/mutated by this scheduler, so it can never
    // silently regain TRIAL (or any other) status through this path.
    @Test
    void job_neverQueriesTerminalStatuses() {
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of());

        renewalAndGraceJob.process();

        verify(tenantRepository, never()).findAllByStatus(SubscriptionStatus.TRIAL_EXPIRED);
        verify(tenantRepository, never()).findAllByStatus(SubscriptionStatus.SUSPENDED);
        verify(tenantRepository, never()).findAllByStatus(SubscriptionStatus.CANCELLED);
        verify(tenantRepository, never()).findAllByStatus(SubscriptionStatus.TRIAL);
    }

    // ── processRenewalReminders: dedup (Behavior 10) ────────────────────────────────

    @Test
    void renewalReminders_dedupColumnsPreventDuplicateSendsOnSecondRunAtSameDayCount() {
        // A small buffer above the exact 5-day mark absorbs test-execution latency —
        // Duration.between(...).toDays() truncates towards zero, so a currentPeriodEnd
        // computed at exactly +5 days can read as 4 days remaining a few ms later.
        Tenant tenant = Tenant.builder()
                .id(7L).name("Agency").email("agency@test.com")
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(5).plusMinutes(1))
                .build();
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(tenant));
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of());

        renewalAndGraceJob.process();
        assertThat(tenant.getRenewalReminder5SentAt()).isNotNull();
        verify(platformEmailService, times(1)).sendRenewalUpcoming(tenant, 5);

        renewalAndGraceJob.process();
        verify(platformEmailService, times(1)).sendRenewalUpcoming(tenant, 5);
    }

    @Test
    void renewalReminders_threeAndOneDayVariants_eachSentExactlyOnce() {
        Tenant threeDayTenant = Tenant.builder()
                .id(8L).name("Agency3").email("agency3@test.com")
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(3).plusMinutes(1))
                .build();
        Tenant oneDayTenant = Tenant.builder()
                .id(9L).name("Agency1").email("agency1@test.com")
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(1).plusMinutes(1))
                .build();
        when(tenantRepository.findAllByStatus(SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(threeDayTenant, oneDayTenant));
        when(tenantRepository.findAllByStatus(SubscriptionStatus.GRACE_PERIOD)).thenReturn(List.of());

        renewalAndGraceJob.process();
        renewalAndGraceJob.process();

        verify(platformEmailService, times(1)).sendRenewalUpcoming(threeDayTenant, 3);
        verify(platformEmailService, times(1)).sendRenewalUpcoming(oneDayTenant, 1);
    }
}
