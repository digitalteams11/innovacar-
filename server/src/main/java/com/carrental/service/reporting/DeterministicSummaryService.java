package com.carrental.service.reporting;

import com.carrental.dto.reporting.ReportDataset;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based executive summary that never depends on an AI provider — spec
 * section 9. Always available; used verbatim when AI is disabled/unavailable,
 * and as the safety net whenever {@code AiReportSummaryService} fails.
 */
@Service
public class DeterministicSummaryService {

    private static final BigDecimal OVERDUE_THRESHOLD = BigDecimal.valueOf(5000);
    private static final BigDecimal LOW_UTILIZATION_THRESHOLD = BigDecimal.valueOf(40);
    private static final BigDecimal SIGNIFICANT_CHANGE_PERCENT = BigDecimal.valueOf(15);

    public List<String> buildSummary(ReportDataset dataset) {
        List<String> lines = new ArrayList<>();
        ReportDataset.FinancialSummary financial = dataset.financial();
        ReportDataset.PeriodComparison comparison = dataset.comparison();

        if (comparison.profit().percentAvailable()) {
            if (financial.profit().compareTo(comparison.profit().previousValue()) > 0) {
                lines.add("Profit improved compared with the previous period.");
            } else if (financial.profit().compareTo(comparison.profit().previousValue()) < 0) {
                lines.add("Profit declined compared with the previous period.");
            }
        } else if (financial.profit().signum() > 0) {
            lines.add("The period closed with a positive profit.");
        }

        if (financial.loss().signum() > 0) {
            lines.add("The period closed with a loss of " + money(financial.loss()) + ".");
        }

        if (comparison.maintenanceCost().percentAvailable()
                && comparison.maintenanceCost().percentChange().compareTo(SIGNIFICANT_CHANGE_PERCENT) > 0) {
            lines.add("Maintenance expenses increased significantly and should be reviewed.");
        }

        if (financial.outstandingBalance().compareTo(OVERDUE_THRESHOLD) > 0) {
            lines.add("A high outstanding balance requires collection follow-up.");
        }

        if (dataset.operations().occupancyRate().compareTo(LOW_UTILIZATION_THRESHOLD) < 0) {
            lines.add("Fleet utilization is below the target level.");
        }

        if (!dataset.lowVehicles().isEmpty()) {
            ReportDataset.VehiclePerformance worst = dataset.lowVehicles().get(0);
            if (worst.profitContribution().signum() < 0) {
                lines.add("Vehicle " + worst.label() + " had a negative profit contribution this period.");
            }
        }

        if (lines.isEmpty()) {
            lines.add("Business activity remained stable compared with the previous period.");
        }
        return lines;
    }

    private String money(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + " MAD";
    }
}
