package com.carrental.service.reporting;

import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.dto.reporting.ReportDataset;
import com.carrental.service.AiGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Generates the natural-language executive summary from an already-computed,
 * verified {@link ReportDataset} — spec section 8. The AI never calculates
 * figures and can never override them; it only explains numbers the backend
 * already produced. Only summarized totals are sent to the model — no
 * passwords, tokens, identity documents, or full client/contract records.
 *
 * <p>Any failure (disabled AI, no active provider, timeout, quota) falls back
 * to {@link DeterministicSummaryService} and reports {@code aiSummaryUsed=false}
 * — a report must never fail to generate because AI is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportSummaryService {

    private static final String AUTOMATION_CODE = "REPORT_EXECUTIVE_SUMMARY";

    private final AiGatewayService aiGatewayService;
    private final DeterministicSummaryService deterministicSummaryService;

    public record SummaryResult(List<String> lines, boolean aiUsed) {}

    public SummaryResult buildSummary(ReportDataset dataset, String language) {
        try {
            String systemInstruction = "You are a financial reporting assistant for the Innovacar car-rental platform. "
                    + "You are given verified, already-calculated business figures for one reporting period. "
                    + "Write a short executive summary (4-6 bullet points, plain text, one per line, no markdown) "
                    + "covering: overall financial health, notable changes vs. the previous period, risks, and "
                    + "recommendations. Never invent numbers — only reference the figures given. "
                    + "Write in " + languageName(language) + ".";
            String userPrompt = buildPrompt(dataset);
            AiExecuteResponse response = aiGatewayService.execute(AUTOMATION_CODE, systemInstruction, userPrompt);
            List<String> lines = response.getContent().lines()
                    .map(String::strip)
                    .filter(l -> !l.isBlank())
                    .map(l -> l.replaceFirst("^[-*•]\\s*", ""))
                    .toList();
            if (lines.isEmpty()) {
                return new SummaryResult(deterministicSummaryService.buildSummary(dataset), false);
            }
            return new SummaryResult(lines, true);
        } catch (Exception e) {
            log.warn("[AI_REPORT_SUMMARY] AI summary generation failed, falling back to deterministic summary: {}", e.getMessage());
            return new SummaryResult(deterministicSummaryService.buildSummary(dataset), false);
        }
    }

    private String buildPrompt(ReportDataset dataset) {
        ReportDataset.FinancialSummary f = dataset.financial();
        ReportDataset.OperationsSummary o = dataset.operations();
        ReportDataset.FleetSummary fl = dataset.fleet();
        ReportDataset.PeriodComparison c = dataset.comparison();
        return """
                Period: %s to %s
                Gross revenue: %s MAD (previous period change: %s)
                Net revenue: %s MAD
                Expenses: %s MAD (previous period change: %s)
                Profit: %s MAD (previous period change: %s)
                Loss: %s MAD
                Outstanding balance: %s MAD (previous period change: %s)
                Overdue payments: %s MAD
                Total reservations: %d, cancelled: %d
                Active contracts: %d, completed contracts: %d
                Fleet utilization rate: %s%%
                Fleet size: %d vehicles (%d available, %d rented, %d in maintenance)
                Maintenance total cost: %s MAD (previous period change: %s)
                """.formatted(
                dataset.periodStart(), dataset.periodEnd(),
                f.grossRevenue(), percent(c.revenue()),
                f.netRevenue(),
                f.expenses(), percent(c.expenses()),
                f.profit(), percent(c.profit()),
                f.loss(),
                f.outstandingBalance(), percent(c.outstandingBalance()),
                f.overduePayments(),
                o.totalReservations(), o.cancelledReservations(),
                o.activeContracts(), o.completedContracts(),
                fl.fleetUtilizationRate(),
                fl.totalVehicles(), fl.availableVehicles(), fl.rentedVehicles(), fl.maintenanceVehicles(),
                dataset.maintenance().totalCost(), percent(c.maintenanceCost()));
    }

    private String percent(ReportDataset.MetricChange change) {
        return change.percentAvailable() ? (change.percentChange() + "%") : "N/A";
    }

    private String languageName(String language) {
        String lang = language != null ? language.toLowerCase(Locale.ROOT) : "fr";
        return switch (lang) {
            case "en" -> "English";
            case "ar" -> "Arabic";
            default -> "French";
        };
    }
}
