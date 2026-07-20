package com.carrental.service;

import com.carrental.entity.Notification;
import com.carrental.entity.Tenant;
import com.carrental.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialExpiryJobTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private PlatformEmailService platformEmailService;
    @Mock private NotificationService notificationService;
    @Mock private AutomationRunRecorder automationRunRecorder;

    @InjectMocks
    private TrialExpiryJob trialExpiryJob;

    @Test
    void expiresTrialPastEndDateAndSendsExpiryNotificationOnce() {
        Tenant tenant = Tenant.builder()
                .id(1L).name("Agency").email("agency@test.com")
                .status("TRIAL").subscriptionActive(true)
                .trialStartDate(LocalDate.now().minusMonths(1).minusDays(1))
                .trialEndDate(LocalDate.now().minusDays(1))
                .build();
        when(tenantRepository.findAllByStatusIgnoreCase("TRIAL")).thenReturn(List.of(tenant));
        when(tenantRepository.countByStatusIgnoreCase("ACTIVE")).thenReturn(5L);

        trialExpiryJob.processTrials();

        assertThat(tenant.getStatus()).isEqualTo("EXPIRED");
        assertThat(tenant.isSubscriptionActive()).isFalse();
        assertThat(tenant.getTrialExpiredNotifiedAt()).isNotNull();
        verify(platformEmailService, times(1)).sendTrialExpired(1L, "agency@test.com", "Agency");
        verify(notificationService, times(1)).createNotification(
                anyString(), anyString(), eq(Notification.NotificationType.WARNING), isNull(), eq(1L));
        verify(tenantRepository).save(tenant);
    }

    @Test
    void doesNotResendExpiryNotificationOnSubsequentRuns() {
        Tenant tenant = Tenant.builder()
                .id(1L).name("Agency").email("agency@test.com")
                .status("TRIAL").subscriptionActive(true)
                .trialStartDate(LocalDate.now().minusMonths(2))
                .trialEndDate(LocalDate.now().minusDays(10))
                .trialExpiredNotifiedAt(LocalDateTime.now().minusDays(9))
                .build();
        when(tenantRepository.findAllByStatusIgnoreCase("TRIAL")).thenReturn(List.of(tenant));
        when(tenantRepository.countByStatusIgnoreCase("ACTIVE")).thenReturn(0L);

        trialExpiryJob.processTrials();

        verify(platformEmailService, never()).sendTrialExpired(any(), any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    void sendsSevenDayReminderExactlyOnce() {
        Tenant tenant = Tenant.builder()
                .id(2L).name("Agency").email("agency@test.com")
                .status("TRIAL").subscriptionActive(true)
                .trialStartDate(LocalDate.now().minusDays(23))
                .trialEndDate(LocalDate.now().plusDays(7))
                .build();
        when(tenantRepository.findAllByStatusIgnoreCase("TRIAL")).thenReturn(List.of(tenant));
        when(tenantRepository.countByStatusIgnoreCase("ACTIVE")).thenReturn(0L);

        trialExpiryJob.processTrials();

        assertThat(tenant.getStatus()).isEqualTo("TRIAL");
        assertThat(tenant.getTrialReminder7SentAt()).isNotNull();
        verify(platformEmailService, times(1)).sendTrialReminder(2L, "agency@test.com", "Agency", 7);

        // A second pass on the same day must not resend.
        trialExpiryJob.processTrials();
        verify(platformEmailService, times(1)).sendTrialReminder(2L, "agency@test.com", "Agency", 7);
    }

    @Test
    void skipsTenantsWithNoTrialEndDateWithoutError() {
        Tenant tenant = Tenant.builder()
                .id(3L).name("Agency").email("agency@test.com")
                .status("TRIAL").subscriptionActive(true)
                .build();
        when(tenantRepository.findAllByStatusIgnoreCase("TRIAL")).thenReturn(List.of(tenant));
        when(tenantRepository.countByStatusIgnoreCase("ACTIVE")).thenReturn(0L);

        trialExpiryJob.processTrials();

        assertThat(tenant.getStatus()).isEqualTo("TRIAL");
        verifyNoInteractions(platformEmailService);
        verify(tenantRepository, never()).save(any());
    }
}
