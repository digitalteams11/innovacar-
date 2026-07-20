package com.carrental.service;

import com.carrental.entity.AutomationAgent;
import com.carrental.entity.AutomationAlert;
import com.carrental.entity.AutomationRun;
import com.carrental.exception.PremiumFeatureRequiredException;
import com.carrental.repository.AutomationAgentRepository;
import com.carrental.repository.AutomationAlertRepository;
import com.carrental.repository.AutomationRunRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Backs the Automation Center (Phase 1: 3 real agents — SUBSCRIPTION_TRIAL,
 * GPS_MONITORING, BACKUP_VERIFICATION; the other 7 from the full spec are not
 * implemented yet, and deliberately not shown as if they were — see
 * AutomationAgentKeys javadoc). Every number returned here comes from real
 * automation_agents/automation_runs/automation_alerts rows, never a hardcoded
 * or estimated figure.
 */
@Service
@RequiredArgsConstructor
public class AutomationService {

    private static final String FEATURE_CODE = "AUTOMATION_CENTER";
    private static final List<String> AGENT_KEYS = List.of(
            AutomationAgentKeys.SUBSCRIPTION_TRIAL,
            AutomationAgentKeys.GPS_MONITORING,
            AutomationAgentKeys.BACKUP_VERIFICATION);

    private final AutomationAgentRepository agentRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationAlertRepository alertRepository;
    private final FeatureAccessService featureAccessService;

    /** Every public method here must go through this first — never rely only on the frontend lock. */
    public void assertAutomationCenterAccess() {
        if (!featureAccessService.isEnabledForCurrentTenant(FEATURE_CODE)) {
            throw new PremiumFeatureRequiredException(FEATURE_CODE,
                    "Automation Center is available in the Premium plan.");
        }
    }

    // Not readOnly: agentsFor() lazily inserts a state row for an agent this tenant
    // hasn't been seen for yet, which a readOnly transaction would reject at the
    // database level (Postgres actually enforces "read only" on the connection).
    @Transactional
    public Map<String, Object> overview() {
        assertAutomationCenterAccess();
        Long tenantId = TenantContext.getCurrentTenantId();

        List<AutomationAgent> agents = agentsFor(tenantId);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<AutomationRun> runsToday = Stream.concat(
                        runRepository.findAllByTenantIdAndStartedAtAfter(tenantId, todayStart).stream(),
                        runRepository.findAllByTenantIdIsNullAndStartedAtAfter(todayStart).stream())
                .toList();

        long successToday = runsToday.stream().filter(r -> r.getStatus() == AutomationRun.Status.SUCCESS).count();
        long failedToday = runsToday.stream().filter(r -> r.getStatus() == AutomationRun.Status.FAILED).count();
        long activeAgents = agents.stream().filter(a -> Boolean.TRUE.equals(a.getEnabled())
                && a.getStatus() != AutomationAgent.Status.DISABLED).count();
        long openAlerts = alertRepository.findAllByTenantIdOrTenantIdIsNullOrderByCreatedAtDesc(tenantId).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getAcknowledged()))
                .count();

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("activeAgents", activeAgents);
        value.put("totalAgents", (long) AGENT_KEYS.size());
        value.put("runsToday", (long) runsToday.size());
        value.put("successfulRunsToday", successToday);
        value.put("failedRunsToday", failedToday);
        value.put("openAlerts", openAlerts);
        value.put("agents", agents.stream().map(this::agentMap).toList());
        return value;
    }

    @Transactional
    public List<Map<String, Object>> agents() {
        assertAutomationCenterAccess();
        return agentsFor(TenantContext.getCurrentTenantId()).stream().map(this::agentMap).toList();
    }

    @Transactional
    public Map<String, Object> setAgentEnabled(String agentKey, boolean enabled) {
        assertAutomationCenterAccess();
        Long tenantId = TenantContext.getCurrentTenantId();
        if (!AGENT_KEYS.contains(agentKey)) {
            throw new IllegalArgumentException("Unknown automation agent: " + agentKey);
        }
        AutomationAgent agent = agentRepository.findByTenantIdAndAgentKey(tenantId, agentKey)
                .orElseGet(() -> AutomationAgent.builder().tenantId(tenantId).agentKey(agentKey).build());
        agent.setEnabled(enabled);
        agent.setStatus(enabled ? AutomationAgent.Status.ACTIVE : AutomationAgent.Status.DISABLED);
        return agentMap(agentRepository.save(agent));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> runs() {
        assertAutomationCenterAccess();
        Long tenantId = TenantContext.getCurrentTenantId();
        return Stream.concat(
                        runRepository.findAllByTenantIdOrderByStartedAtDesc(tenantId).stream(),
                        runRepository.findAllByTenantIdIsNullOrderByStartedAtDesc().stream())
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .limit(100)
                .map(this::runMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> alerts() {
        assertAutomationCenterAccess();
        Long tenantId = TenantContext.getCurrentTenantId();
        return alertRepository.findAllByTenantIdOrTenantIdIsNullOrderByCreatedAtDesc(tenantId).stream()
                .map(this::alertMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> acknowledgeAlert(Long alertId, Long userId) {
        assertAutomationCenterAccess();
        AutomationAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Alert not found"));
        Long tenantId = TenantContext.getCurrentTenantId();
        if (alert.getTenantId() != null && !alert.getTenantId().equals(tenantId)) {
            throw new PremiumFeatureRequiredException(FEATURE_CODE, "Automation Center is available in the Premium plan.");
        }
        alert.setAcknowledged(true);
        alert.setAcknowledgedBy(userId);
        alert.setAcknowledgedAt(LocalDateTime.now());
        return alertMap(alertRepository.save(alert));
    }

    /** Ensures a state row exists for every implemented agent so the dashboard never shows a gap. */
    private List<AutomationAgent> agentsFor(Long tenantId) {
        return AGENT_KEYS.stream()
                .map(key -> agentRepository.findByTenantIdAndAgentKey(tenantId, key)
                        .orElseGet(() -> agentRepository.save(AutomationAgent.builder()
                                .tenantId(tenantId).agentKey(key).build())))
                .toList();
    }

    private Map<String, Object> agentMap(AutomationAgent agent) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", agent.getId());
        value.put("key", agent.getAgentKey());
        value.put("enabled", agent.getEnabled());
        value.put("status", agent.getStatus());
        value.put("lastRunAt", agent.getLastRunAt());
        value.put("lastSuccessAt", agent.getLastSuccessAt());
        value.put("lastFailureAt", agent.getLastFailureAt());
        value.put("successCount", agent.getSuccessCount());
        value.put("failureCount", agent.getFailureCount());
        return value;
    }

    private Map<String, Object> runMap(AutomationRun run) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", run.getId());
        value.put("agentKey", run.getAgentKey());
        value.put("status", run.getStatus());
        value.put("startedAt", run.getStartedAt());
        value.put("completedAt", run.getCompletedAt());
        value.put("durationMs", run.getDurationMs());
        value.put("resultSummary", run.getResultSummary());
        value.put("errorCode", run.getErrorCode());
        value.put("sanitizedErrorMessage", run.getSanitizedErrorMessage());
        return value;
    }

    private Map<String, Object> alertMap(AutomationAlert alert) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", alert.getId());
        value.put("agentKey", alert.getAgentKey());
        value.put("severity", alert.getSeverity());
        value.put("title", alert.getTitle());
        value.put("message", alert.getMessage());
        value.put("acknowledged", alert.getAcknowledged());
        value.put("createdAt", alert.getCreatedAt());
        return value;
    }
}
