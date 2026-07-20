package com.carrental.service;

import com.carrental.entity.AutomationAgent;
import com.carrental.entity.AutomationRun;
import com.carrental.exception.PremiumFeatureRequiredException;
import com.carrental.repository.AutomationAgentRepository;
import com.carrental.repository.AutomationAlertRepository;
import com.carrental.repository.AutomationRunRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {

    @Mock private AutomationAgentRepository agentRepository;
    @Mock private AutomationRunRepository runRepository;
    @Mock private AutomationAlertRepository alertRepository;
    @Mock private FeatureAccessService featureAccessService;

    @InjectMocks
    private AutomationService automationService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void overview_notPremium_throwsPremiumFeatureRequired() {
        when(featureAccessService.isEnabledForCurrentTenant("AUTOMATION_CENTER")).thenReturn(false);

        assertThatThrownBy(() -> automationService.overview())
                .isInstanceOf(PremiumFeatureRequiredException.class)
                .extracting(e -> ((PremiumFeatureRequiredException) e).getFeature())
                .isEqualTo("AUTOMATION_CENTER");

        verifyNoInteractions(agentRepository, runRepository);
    }

    @Test
    void overview_premium_returnsRealAggregatedData_neverFabricated() {
        when(featureAccessService.isEnabledForCurrentTenant("AUTOMATION_CENTER")).thenReturn(true);
        when(agentRepository.findByTenantIdAndAgentKey(eq(1L), anyString())).thenAnswer(invocation ->
                java.util.Optional.of(AutomationAgent.builder()
                        .tenantId(1L).agentKey(invocation.getArgument(1))
                        .enabled(true).status(AutomationAgent.Status.ACTIVE).build()));
        when(runRepository.findAllByTenantIdAndStartedAtAfter(eq(1L), any())).thenReturn(List.of(
                AutomationRun.builder().tenantId(1L).agentKey("GPS_MONITORING")
                        .status(AutomationRun.Status.SUCCESS).startedAt(LocalDateTime.now()).build(),
                AutomationRun.builder().tenantId(1L).agentKey("GPS_MONITORING")
                        .status(AutomationRun.Status.FAILED).startedAt(LocalDateTime.now()).build()));
        when(runRepository.findAllByTenantIdIsNullAndStartedAtAfter(any())).thenReturn(List.of());
        when(alertRepository.findAllByTenantIdOrTenantIdIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        var overview = automationService.overview();

        assertThat(overview.get("runsToday")).isEqualTo(2L);
        assertThat(overview.get("successfulRunsToday")).isEqualTo(1L);
        assertThat(overview.get("failedRunsToday")).isEqualTo(1L);
        assertThat(overview.get("activeAgents")).isEqualTo(3L);
    }

    @Test
    void setAgentEnabled_unknownKey_rejected() {
        when(featureAccessService.isEnabledForCurrentTenant("AUTOMATION_CENTER")).thenReturn(true);

        assertThatThrownBy(() -> automationService.setAgentEnabled("NOT_A_REAL_AGENT", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acknowledgeAlert_crossTenantAlert_isDenied() {
        when(featureAccessService.isEnabledForCurrentTenant("AUTOMATION_CENTER")).thenReturn(true);
        when(alertRepository.findById(99L)).thenReturn(java.util.Optional.of(
                com.carrental.entity.AutomationAlert.builder().id(99L).tenantId(2L).agentKey("GPS_MONITORING").title("x").build()));

        assertThatThrownBy(() -> automationService.acknowledgeAlert(99L, 7L))
                .isInstanceOf(PremiumFeatureRequiredException.class);
    }
}
