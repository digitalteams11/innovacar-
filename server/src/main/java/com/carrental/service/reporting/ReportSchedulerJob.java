package com.carrental.service.reporting;

import com.carrental.entity.ReportType;
import com.carrental.entity.Tenant;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily-safe monthly/yearly report scheduler — spec section 15.
 *
 * <p>Deliberately runs every day (not just "on the 1st"/"on Jan 2nd") and lets
 * {@link ReportGenerationService}'s idempotency check (backed by the
 * {@code uk_report_period} DB constraint) decide whether there is actually a
 * missing report to create. A single exact per-day cron firing is not
 * reliable here — Railway restarts can cause a missed exact-time trigger — so
 * "run daily, skip if already done" is the safety net the spec calls for.
 * One tenant's failure is isolated and never stops the batch for the rest,
 * matching {@code VehicleStatusScheduler}'s tenant-loop pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSchedulerJob {

    private final TenantRepository tenantRepository;
    private final ReportGenerationService reportGenerationService;

    @Scheduled(cron = "${app.reports.monthly.cron:0 0 2 * * *}")
    public void runMonthly() {
        runForAllTenants(ReportType.MONTHLY);
    }

    @Scheduled(cron = "${app.reports.yearly.cron:0 0 3 * * *}")
    public void runYearly() {
        runForAllTenants(ReportType.YEARLY);
    }

    private void runForAllTenants(ReportType type) {
        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) return;
        LocalDate today = LocalDate.now();

        int generated = 0;
        int skipped = 0;
        int failed = 0;
        for (Tenant tenant : tenants) {
            Long tenantId = tenant.getId();
            try {
                TenantContext.setCurrentTenantId(tenantId);
                ReportGenerationService.GenerationOutcome outcome = reportGenerationService
                        .generateScheduled(tenantId, type, today);
                if (outcome.skipReason() != null) {
                    skipped++;
                } else if (outcome.report() != null && outcome.report().getStatus() != null
                        && outcome.report().getStatus().name().equals("FAILED")) {
                    failed++;
                } else {
                    generated++;
                }
            } catch (Exception e) {
                failed++;
                log.error("[REPORT_SCHEDULER] {} report generation failed for tenantId={}: {}",
                        type, tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("[REPORT_SCHEDULER] {} run complete: generated={} skipped={} failed={} totalTenants={}",
                type, generated, skipped, failed, tenants.size());
    }
}
