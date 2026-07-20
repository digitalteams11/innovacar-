package com.carrental.service;

import com.carrental.entity.AutomationAlert;
import com.carrental.entity.GpsDevice;
import com.carrental.repository.GpsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Automation Center's GPS Monitoring Agent — a periodic, tenant-scoped observability
 * layer over GPS device state, deliberately kept separate from {@link GpsSchedulerService}
 * (the actual device-polling job, which runs every 60s and is left completely untouched
 * here). This agent only reads the state that job already produces and records it as real
 * automation run/alert history for Premium tenants, at a coarser 5-minute interval so the
 * automation_runs table doesn't fill with a row every 60 seconds.
 *
 * <p>Premium-gated per tenant: a tenant without {@code AUTOMATION_CENTER} enabled (Basic/
 * Standard/expired Premium) is skipped entirely — no background work happens for them,
 * per the "no automation jobs may run for non-Premium tenants" requirement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GpsMonitoringAutomationAgent {

    private final GpsDeviceRepository gpsDeviceRepository;
    private final FeatureAccessService featureAccessService;
    private final AutomationRunRecorder automationRunRecorder;

    @Scheduled(fixedDelayString = "${app.automation.gps-monitoring.interval-ms:300000}")
    public void checkDevices() {
        List<Long> tenantIds = gpsDeviceRepository.findDistinctTenantIds();
        for (Long tenantId : tenantIds) {
            try {
                checkTenant(tenantId);
            } catch (Exception ex) {
                log.error("[AUTOMATION_GPS] Failed to check tenant [{}]: {}", tenantId, ex.getMessage(), ex);
            }
        }
    }

    private void checkTenant(Long tenantId) {
        if (!featureAccessService.isEnabledForTenant(tenantId, "AUTOMATION_CENTER")) {
            return;
        }

        LocalDateTime startedAt = automationRunRecorder.start();
        try {
            List<GpsDevice> devices = gpsDeviceRepository.findAllByTenantId(tenantId);
            long offlineCount = devices.stream()
                    .filter(d -> "OFFLINE".equalsIgnoreCase(d.getStatus()))
                    .count();

            if (offlineCount > 0) {
                automationRunRecorder.raiseAlert(tenantId, AutomationAgentKeys.GPS_MONITORING,
                        AutomationAlert.Severity.WARNING,
                        offlineCount + " GPS device(s) offline",
                        offlineCount + " of " + devices.size() + " configured device(s) are not reporting.");
            }

            String summary = "Checked " + devices.size() + " device(s), " + offlineCount + " offline.";
            automationRunRecorder.recordSuccess(tenantId, AutomationAgentKeys.GPS_MONITORING, startedAt, summary);
        } catch (Exception ex) {
            automationRunRecorder.recordFailure(tenantId, AutomationAgentKeys.GPS_MONITORING, startedAt,
                    "GPS_CHECK_FAILED", "Unable to check GPS device status.");
            throw ex;
        }
    }
}
