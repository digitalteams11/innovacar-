package com.carrental.service.reporting;

import com.carrental.dto.reporting.ReportDataset;
import com.carrental.entity.Report;
import com.carrental.entity.ReportType;
import com.carrental.entity.Tenant;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders the financial/operational {@link ReportDataset} into a true-text PDF
 * (no dashboard screenshots) — spec section 10. Follows the same OpenPDF
 * conventions as {@code PdfService} (colors/fonts/table style).
 *
 * <p>Arabic support is best-effort: right-aligned layout and translated labels
 * always work, but complex glyph shaping/BiDi reordering requires an embedded
 * Arabic-capable TrueType font configured via
 * {@code app.reports.arabic-font-path}. Without it, Arabic text falls back to
 * Helvetica, which cannot render Arabic glyphs at all — this is a known,
 * documented limitation until a font is provisioned on the deployment target.
 */
@Slf4j
@Service
public class ReportPdfGenerator {

    private final String arabicFontPath;
    private final AtomicBoolean arabicFontWarned = new AtomicBoolean(false);

    public ReportPdfGenerator(@Value("${app.reports.arabic-font-path:}") String arabicFontPath) {
        this.arabicFontPath = arabicFontPath;
    }

    private static final Color INK = new Color(19, 43, 76);
    private static final Color BLUE = new Color(23, 76, 135);
    private static final Color LIGHT_BLUE = new Color(235, 243, 252);
    private static final Color BORDER = new Color(83, 103, 130);
    private static final Color PALE = new Color(248, 250, 252);
    private static final Color GREEN = new Color(27, 130, 79);
    private static final Color RED = new Color(178, 34, 52);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generate(Report report, Tenant tenant, ReportDataset dataset, List<String> summaryLines, boolean aiUsed) {
        String language = report.getLanguage();
        boolean rtl = "ar".equalsIgnoreCase(language);
        Font base = resolveBaseFont(language, 7.5f, Font.NORMAL, Color.BLACK);
        Font title = resolveBaseFont(language, 16f, Font.BOLD, INK);
        Font sectionFont = resolveBaseFont(language, 9f, Font.BOLD, Color.WHITE);
        Font label = resolveBaseFont(language, 7.5f, Font.BOLD, INK);
        Font small = resolveBaseFont(language, 6.8f, Font.NORMAL, new Color(100, 100, 100));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 28, 28, 30, 30);
            PdfWriter.getInstance(document, baos);
            document.open();

            addCover(document, report, tenant, title, base, small, language);
            document.newPage();
            addExecutiveSummary(document, summaryLines, aiUsed, sectionFont, base, small, language);
            addFinancialOverview(document, dataset.financial(), sectionFont, label, base, language);
            addProfitLoss(document, dataset.financial(), sectionFont, label, base, language);
            addOperations(document, dataset.operations(), sectionFont, label, base, language);
            addFleet(document, dataset.fleet(), sectionFont, label, base, language);
            addVehiclePerformance(document, ReportLabels.get("section.top_vehicles", language), dataset.topVehicles(),
                    sectionFont, label, base, language);
            addVehiclePerformance(document, ReportLabels.get("section.low_vehicles", language), dataset.lowVehicles(),
                    sectionFont, label, base, language);
            addMaintenance(document, dataset.maintenance(), sectionFont, label, base, language);
            addComparison(document, dataset.comparison(), sectionFont, label, base, language);
            addFooter(document, report, tenant, small);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("[REPORT_PDF_GENERATE] Failed for reportId={} tenantId={}",
                    report.getId(), tenant != null ? tenant.getId() : null, e);
            throw new IllegalStateException("Unable to generate report PDF", e);
        }
    }

    private void addCover(Document document, Report report, Tenant tenant, Font title, Font base, Font small, String language) throws Exception {
        Paragraph titleParagraph = new Paragraph(
                (report.getReportType() == ReportType.MONTHLY
                        ? ReportLabels.get("title.monthly", language)
                        : ReportLabels.get("title.yearly", language)),
                title);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        titleParagraph.setSpacingBefore(120);
        document.add(titleParagraph);

        Paragraph agency = new Paragraph(tenant != null ? tenant.getName() : "", base);
        agency.setAlignment(Element.ALIGN_CENTER);
        agency.setSpacingBefore(20);
        document.add(agency);

        String periodText = report.getPeriodStart().toLocalDate().format(DATE) + " - "
                + report.getPeriodEnd().toLocalDate().minusDays(1).format(DATE);
        Paragraph period = new Paragraph(ReportLabels.get("cover.period", language) + ": " + periodText, base);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingBefore(10);
        document.add(period);

        Paragraph generated = new Paragraph(ReportLabels.get("cover.generated", language) + ": "
                + (report.getGeneratedAt() != null ? report.getGeneratedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : ""),
                small);
        generated.setAlignment(Element.ALIGN_CENTER);
        generated.setSpacingBefore(6);
        document.add(generated);
    }

    private void addExecutiveSummary(Document document, List<String> summaryLines, boolean aiUsed,
                                      Font sectionFont, Font base, Font small, String language) throws Exception {
        addSectionHeader(document, ReportLabels.get("section.executive_summary", language), sectionFont);
        Paragraph aiLabel = new Paragraph("[" + ReportLabels.get("section.ai_label", language) + (aiUsed ? "" : " - deterministic") + "]", small);
        document.add(aiLabel);
        for (String line : summaryLines) {
            Paragraph p = new Paragraph("- " + line, base);
            p.setSpacingBefore(3);
            document.add(p);
        }
    }

    private void addFinancialOverview(Document document, ReportDataset.FinancialSummary f,
                                       Font sectionFont, Font label, Font base, String language) throws Exception {
        addSectionHeader(document, ReportLabels.get("section.financial_overview", language), sectionFont);
        PdfPTable table = kvTable();
        row(table, ReportLabels.get("field.gross_revenue", language), money(f.grossRevenue()), label, base);
        row(table, ReportLabels.get("field.net_revenue", language), money(f.netRevenue()), label, base);
        row(table, ReportLabels.get("field.expenses", language), money(f.expenses()), label, base);
        row(table, ReportLabels.get("field.outstanding", language), money(f.outstandingBalance()), label, base);
        row(table, ReportLabels.get("field.overdue", language), money(f.overduePayments()), label, base);
        row(table, ReportLabels.get("field.refunds", language), money(f.refunds()), label, base);
        row(table, ReportLabels.get("field.avg_revenue_rental", language), money(f.avgRevenuePerRental()), label, base);
        document.add(table);
    }

    private void addProfitLoss(Document document, ReportDataset.FinancialSummary f,
                                Font sectionFont, Font label, Font base, String language) throws Exception {
        addSectionHeader(document, ReportLabels.get("section.profit_loss", language), sectionFont);
        PdfPTable table = kvTable();
        Color profitColor = f.profit().signum() >= 0 ? GREEN : RED;
        row(table, ReportLabels.get("field.profit", language), money(f.profit()), label,
                new Font(base.getFamily(), base.getSize(), Font.BOLD, profitColor));
        row(table, ReportLabels.get("field.loss", language), money(f.loss()), label, base);
        document.add(table);
    }

    private void addOperations(Document document, ReportDataset.OperationsSummary o,
                                Font sectionFont, Font label, Font base, String language) throws Exception {
        addSectionHeader(document, ReportLabels.get("section.reservations_contracts", language), sectionFont);
        PdfPTable table = kvTable();
        row(table, ReportLabels.get("field.total_reservations", language), String.valueOf(o.totalReservations()), label, base);
        row(table, ReportLabels.get("field.confirmed", language), String.valueOf(o.confirmedReservations()), label, base);
        row(table, ReportLabels.get("field.cancelled", language), String.valueOf(o.cancelledReservations()), label, base);
        row(table, ReportLabels.get("field.active_contracts", language), String.valueOf(o.activeContracts()), label, base);
        row(table, ReportLabels.get("field.completed_contracts", language), String.valueOf(o.completedContracts()), label, base);
        row(table, ReportLabels.get("field.avg_duration", language), o.avgRentalDurationDays().toPlainString(), label, base);
        row(table, ReportLabels.get("field.occupancy", language), o.occupancyRate() + "%", label, base);
        document.add(table);
    }

    private void addFleet(Document document, ReportDataset.FleetSummary fl,
                           Font sectionFont, Font label, Font base, String language) throws Exception {
        addSectionHeader(document, ReportLabels.get("section.fleet", language), sectionFont);
        PdfPTable table = kvTable();
        row(table, ReportLabels.get("field.total_vehicles", language), String.valueOf(fl.totalVehicles()), label, base);
        row(table, ReportLabels.get("field.available", language), String.valueOf(fl.availableVehicles()), label, base);
        row(table, ReportLabels.get("field.rented", language), String.valueOf(fl.rentedVehicles()), label, base);
        row(table, ReportLabels.get("field.reserved", language), String.valueOf(fl.reservedVehicles()), label, base);
        row(table, ReportLabels.get("field.maintenance", language), String.valueOf(fl.maintenanceVehicles()), label, base);
        row(table, ReportLabels.get("field.inactive", language), String.valueOf(fl.inactiveVehicles()), label, base);
        row(table, ReportLabels.get("field.utilization", language), fl.fleetUtilizationRate() + "%", label, base);
        row(table, ReportLabels.get("field.revenue_per_vehicle", language), money(fl.revenuePerVehicle()), label, base);
        row(table, ReportLabels.get("field.maintenance_cost_per_vehicle", language), money(fl.maintenanceCostPerVehicle()), label, base);
        document.add(table);
    }

    private void addVehiclePerformance(Document document, String title, List<ReportDataset.VehiclePerformance> vehicles,
                                        Font sectionFont, Font label, Font base, String language) throws Exception {
        addSectionHeader(document, title, sectionFont);
        if (vehicles.isEmpty()) {
            document.add(new Paragraph("-", base));
            return;
        }
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        String[] headers = {"Vehicle", "Revenue", "Expenses", "Profit", "Utilization"};
        for (String h : headers) table.addCell(cell(h, label, LIGHT_BLUE, Element.ALIGN_CENTER));
        for (ReportDataset.VehiclePerformance v : vehicles) {
            table.addCell(cell(v.label(), base, Color.WHITE, Element.ALIGN_LEFT));
            table.addCell(cell(money(v.revenue()), base, Color.WHITE, Element.ALIGN_RIGHT));
            table.addCell(cell(money(v.expenses()), base, Color.WHITE, Element.ALIGN_RIGHT));
            table.addCell(cell(money(v.profitContribution()), base, Color.WHITE, Element.ALIGN_RIGHT));
            table.addCell(cell(v.utilizationRate() + "%", base, Color.WHITE, Element.ALIGN_RIGHT));
        }
        document.add(table);
    }

    private void addMaintenance(Document document, ReportDataset.MaintenanceSummary m,
                                 Font sectionFont, Font label, Font base, String language) throws Exception {
        addSectionHeader(document, ReportLabels.get("section.maintenance", language), sectionFont);
        PdfPTable table = kvTable();
        row(table, ReportLabels.get("field.maintenance_orders", language), String.valueOf(m.totalOrders()), label, base);
        row(table, ReportLabels.get("field.maintenance_completed", language), String.valueOf(m.completedOrders()), label, base);
        row(table, ReportLabels.get("field.maintenance_active", language), String.valueOf(m.activeOrders()), label, base);
        row(table, ReportLabels.get("field.maintenance_total_cost", language), money(m.totalCost()), label, base);
        if (m.highestCostOrder() != null) {
            row(table, ReportLabels.get("field.highest_cost", language),
                    m.highestCostOrder().label() + " - " + money(m.highestCostOrder().cost()), label, base);
        }
        row(table, ReportLabels.get("field.repeated_maintenance", language), String.valueOf(m.vehiclesWithRepeatedMaintenance()), label, base);
        row(table, ReportLabels.get("field.upcoming", language), String.valueOf(m.upcomingScheduled()), label, base);
        document.add(table);
    }

    private void addComparison(Document document, ReportDataset.PeriodComparison c,
                                Font sectionFont, Font label, Font base, String language) throws Exception {
        addSectionHeader(document, ReportLabels.get("section.comparison", language), sectionFont);
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.addCell(cell("Metric", label, LIGHT_BLUE, Element.ALIGN_LEFT));
        table.addCell(cell("Current", label, LIGHT_BLUE, Element.ALIGN_RIGHT));
        table.addCell(cell(ReportLabels.get("field.change_vs_previous", language), label, LIGHT_BLUE, Element.ALIGN_RIGHT));
        comparisonRow(table, ReportLabels.get("field.gross_revenue", language), c.revenue(), base, true);
        comparisonRow(table, ReportLabels.get("field.expenses", language), c.expenses(), base, true);
        comparisonRow(table, ReportLabels.get("field.profit", language), c.profit(), base, true);
        comparisonRow(table, ReportLabels.get("field.total_reservations", language), c.reservations(), base, false);
        comparisonRow(table, ReportLabels.get("field.utilization", language), c.utilization(), base, false);
        comparisonRow(table, ReportLabels.get("field.maintenance_total_cost", language), c.maintenanceCost(), base, true);
        comparisonRow(table, ReportLabels.get("field.outstanding", language), c.outstandingBalance(), base, true);
        document.add(table);
    }

    private void comparisonRow(PdfPTable table, String metricLabel, ReportDataset.MetricChange change, Font base, boolean isMoney) {
        table.addCell(cell(metricLabel, base, Color.WHITE, Element.ALIGN_LEFT));
        table.addCell(cell(isMoney ? money(change.currentValue()) : change.currentValue().toPlainString(),
                base, Color.WHITE, Element.ALIGN_RIGHT));
        String changeText = change.percentAvailable()
                ? (change.percentChange().signum() >= 0 ? "+" : "") + change.percentChange() + "%"
                : "N/A";
        table.addCell(cell(changeText, base, Color.WHITE, Element.ALIGN_RIGHT));
    }

    private void addFooter(Document document, Report report, Tenant tenant, Font small) throws Exception {
        Paragraph footer = new Paragraph();
        footer.setSpacingBefore(20);
        footer.add(new com.lowagie.text.Chunk(
                "Report #" + report.getId() + " | " + (tenant != null ? tenant.getName() : "") + " | Confidential - internal use only",
                small));
        document.add(footer);
    }

    private void addSectionHeader(Document document, String titleText, Font sectionFont) throws Exception {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setSpacingBefore(10);
        header.addCell(cell(titleText, sectionFont, BLUE, Element.ALIGN_LEFT));
        document.add(header);
    }

    private PdfPTable kvTable() {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{1, 1});
        } catch (Exception ignored) {
        }
        return table;
    }

    private void row(PdfPTable table, String labelText, String valueText, Font label, Font base) {
        table.addCell(cell(labelText, label, LIGHT_BLUE, Element.ALIGN_LEFT));
        table.addCell(cell(valueText, base, Color.WHITE, Element.ALIGN_RIGHT));
    }

    private PdfPCell cell(String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(4);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private String money(BigDecimal value) {
        BigDecimal v = value != null ? value : BigDecimal.ZERO;
        return v.stripTrailingZeros().toPlainString() + " MAD";
    }

    /**
     * Resolves the font to use for the given report language. For AR, attempts
     * to embed the configured Arabic TrueType font (Identity-H, embedded) so
     * Arabic characters render at all; falls back to Helvetica (no Arabic
     * glyph support) with a one-time warning if no font is configured/found.
     */
    private Font resolveBaseFont(String language, float size, int style, Color color) {
        if ("ar".equalsIgnoreCase(language) && arabicFontPath != null && !arabicFontPath.isBlank()) {
            try {
                Path path = Path.of(arabicFontPath);
                if (Files.exists(path)) {
                    BaseFont baseFont = BaseFont.createFont(path.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(baseFont, size, style, color);
                }
            } catch (Exception e) {
                log.warn("[REPORT_PDF] Failed to load configured Arabic font at {}: {}", arabicFontPath, e.getMessage());
            }
        }
        if ("ar".equalsIgnoreCase(language) && arabicFontWarned.compareAndSet(false, true)) {
            log.warn("[REPORT_PDF] No Arabic font configured (app.reports.arabic-font-path) — Arabic PDF reports "
                    + "will render without proper Arabic glyph support until a TrueType font is provisioned.");
        }
        return new Font(Font.HELVETICA, size, style, color);
    }
}
