package com.carrental.service;

import com.carrental.entity.AutomationAgent;
import com.carrental.entity.AutomationAlert;
import com.carrental.repository.AutomationAgentRepository;
import com.carrental.repository.AutomationAlertRepository;
import com.carrental.repository.AutomationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationRunRecorderTest {

    @Mock private AutomationAgentRepository agentRepository;
    @Mock private AutomationRunRepository runRepository;
    @Mock private AutomationAlertRepository alertRepository;

    @InjectMocks
    private AutomationRunRecorder recorder;

    @Test
    void raiseAlert_skipsWhenUnacknowledgedAlertAlreadyExists() {
        when(alertRepository.existsByTenantIdAndAgentKeyAndAcknowledgedFalse(1L, "GPS_MONITORING")).thenReturn(true);

        recorder.raiseAlert(1L, "GPS_MONITORING", AutomationAlert.Severity.WARNING, "title", "message");

        verify(alertRepository, never()).save(any());
    }

    @Test
    void raiseAlert_createsWhenNoUnacknowledgedAlertExists() {
        when(alertRepository.existsByTenantIdAndAgentKeyAndAcknowledgedFalse(1L, "GPS_MONITORING")).thenReturn(false);

        recorder.raiseAlert(1L, "GPS_MONITORING", AutomationAlert.Severity.WARNING, "title", "message");

        verify(alertRepository).save(any());
    }

    @Test
    void recordSuccess_updatesAgentStateCounters() {
        when(agentRepository.findByTenantIdAndAgentKey(1L, "GPS_MONITORING"))
                .thenReturn(Optional.of(AutomationAgent.builder().tenantId(1L).agentKey("GPS_MONITORING")
                        .successCount(4L).failureCount(1L).build()));
        when(agentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.recordSuccess(1L, "GPS_MONITORING", LocalDateTime.now().minusSeconds(1), "ok");

        ArgumentCaptor<AutomationAgent> captor = ArgumentCaptor.forClass(AutomationAgent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccessCount()).isEqualTo(5L);
        assertThat(captor.getValue().getStatus()).isEqualTo(AutomationAgent.Status.ACTIVE);
    }

    @Test
    void recordFailure_updatesAgentStateToError() {
        when(agentRepository.findByTenantIdIsNullAndAgentKey("SUBSCRIPTION_TRIAL"))
                .thenReturn(Optional.of(AutomationAgent.builder().agentKey("SUBSCRIPTION_TRIAL")
                        .successCount(2L).failureCount(0L).build()));
        when(agentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.recordFailure(null, "SUBSCRIPTION_TRIAL", LocalDateTime.now().minusSeconds(1), "SOME_ERROR", "sanitized");

        ArgumentCaptor<AutomationAgent> captor = ArgumentCaptor.forClass(AutomationAgent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getFailureCount()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(AutomationAgent.Status.ERROR);
    }
}
