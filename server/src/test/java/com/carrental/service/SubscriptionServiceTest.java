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

import java.time.LocalDate;

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
