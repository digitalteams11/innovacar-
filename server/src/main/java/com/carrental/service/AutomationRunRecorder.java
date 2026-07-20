package com.carrental.service;

import com.carrental.entity.AutomationAgent;
import com.carrental.entity.AutomationAlert;
import com.carrental.entity.AutomationRun;
import com.carrental.repository.AutomationAgentRepository;
import com.carrental.repository.AutomationAlertRepository;
import com.carrental.repository.AutomationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The single place every automation agent reports through — persists real run
 * history and per-agent state instead of each agent hand-rolling its own
 * bookkeeping. An agent's own business logic (GPS polling, trial expiry, backup
 * creation) is never modified by this class; it's called at the start/end of an
 * already-working method as a pure addition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationRunRecorder {

    private final AutomationAgentRepository agentRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationAlertRepository alertRepository;

    /** Marks the start of a run and returns the timestamp to pass back into recordSuccess/recordFailure. */
    public LocalDateTime start() {
        return LocalDateTime.now();
    }

    @Transactional
    public void recordSuccess(Long tenantId, String agentKey, LocalDateTime startedAt, String resultSummary) {
        LocalDateTime completedAt = LocalDateTime.now();
        runRepository.save(AutomationRun.builder()
                .tenantId(tenantId)
                .agentKey(agentKey)
                .status(AutomationRun.Status.SUCCESS)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .durationMs(ChronoUnit.MILLIS.between(startedAt, completedAt))
                .resultSummary(resultSummary)
                .build());

        AutomationAgent agent = agentState(tenantId, agentKey);
        agent.setStatus(AutomationAgent.Status.ACTIVE);
        agent.setLastRunAt(completedAt);
        agent.setLastSuccessAt(completedAt);
        agent.setSuccessCount(agent.getSuccessCount() + 1);
        agentRepository.save(agent);
    }

    @Transactional
    public void recordFailure(Long tenantId, String agentKey, LocalDateTime startedAt, String errorCode, String sanitizedMessage) {
        LocalDateTime completedAt = LocalDateTime.now();
        runRepository.save(AutomationRun.builder()
                .tenantId(tenantId)
                .agentKey(agentKey)
                .status(AutomationRun.Status.FAILED)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .durationMs(ChronoUnit.MILLIS.between(startedAt, completedAt))
                .errorCode(errorCode)
                .sanitizedErrorMessage(sanitizedMessage)
                .build());

        AutomationAgent agent = agentState(tenantId, agentKey);
        agent.setStatus(AutomationAgent.Status.ERROR);
        agent.setLastRunAt(completedAt);
        agent.setLastFailureAt(completedAt);
        agent.setFailureCount(agent.getFailureCount() + 1);
        agentRepository.save(agent);

        log.warn("[AUTOMATION_RUN_FAILED] agent={} tenantId={} errorCode={} message={}",
                agentKey, tenantId, errorCode, sanitizedMessage);
    }

    /**
     * Raises an alert unless an unacknowledged one already exists for this tenant+agent —
     * an agent that re-checks every few minutes must not flood the alert list with the same
     * condition repeated on every tick. Acknowledging the existing alert (or it resolving,
     * e.g. the device coming back online — see the agent's own logic) allows a new one.
     */
    @Transactional
    public void raiseAlert(Long tenantId, String agentKey, AutomationAlert.Severity severity, String title, String message) {
        if (alertRepository.existsByTenantIdAndAgentKeyAndAcknowledgedFalse(tenantId, agentKey)) {
            return;
        }
        alertRepository.save(AutomationAlert.builder()
                .tenantId(tenantId)
                .agentKey(agentKey)
                .severity(severity)
                .title(title)
                .message(message)
                .build());
    }

    @Transactional
    protected AutomationAgent agentState(Long tenantId, String agentKey) {
        return (tenantId == null
                ? agentRepository.findByTenantIdIsNullAndAgentKey(agentKey)
                : agentRepository.findByTenantIdAndAgentKey(tenantId, agentKey))
                .orElseGet(() -> agentRepository.save(AutomationAgent.builder()
                        .tenantId(tenantId)
                        .agentKey(agentKey)
                        .build()));
    }
}
