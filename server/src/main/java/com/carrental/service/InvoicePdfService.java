package com.carrental.service;

import com.carrental.dto.invoice.InvoiceExportFilter;
import com.carrental.exception.PdfGenerationException;
import com.carrental.entity.Contract;
import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Payment;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.Tenant;
import com.carrental.repository.PaymentRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates real, branded, true-text PDF invoices (individual invoice PDF +
 * filtered invoice-list report) in FR/EN/AR — including working Arabic glyph
 * rendering, which nothing else in the project provides today.
 *
 * <p>Follows the same OpenPDF ({@code com.lowagie.text.*}) conventions as
 * {@link PdfService} (A4, {@code Document}/{@code PdfWriter}, header/footer
 * page-event pattern, cell-styling helpers) but is a fully independent
 * service — it does not call into or modify {@code PdfService} or
 * {@code ReportPdfGenerator}.
 *
 * <p><strong>Arabic support:</strong> unlike {@code ReportPdfGenerator}'s
 * {@code app.reports.arabic-font-path} (a dead code path — no environment
 * configures it), this service loads the bundled Noto Naskh Arabic TTF
 * ({@code /fonts/NotoNaskhArabic-Regular.ttf}) straight from the classpath
 * via {@code getClass().getResourceAsStream(...)}, so it ships inside the
 * jar with zero environment configuration and works identically in every
 * deployment. The loaded {@link BaseFont} is cached after first use.
 */
@Slf4j
@Service
public class InvoicePdfService {

    private final PaymentRepository paymentRepository;

    public InvoicePdfService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    private static final Color INK = new Color(19, 43, 76);
    private static final Color BLUE = new Color(23, 76, 135);
    private static final Color LIGHT_BLUE = new Color(235, 243, 252);
    private static final Color BORDER = new Color(83, 103, 130);
    private static final Color PALE = new Color(248, 250, 252);
    private static final Color GREEN = new Color(27, 130, 79);
    private static final Color RED = new Color(178, 34, 52);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String ARABIC_FONT_RESOURCE = "/fonts/NotoNaskhArabic-Regular.ttf";
    private static final String ARABIC_FONT_NAME = "NotoNaskhArabic-Regular.ttf";

    /** Lazily-loaded, cached Arabic BaseFont — read from the classpath exactly once per JVM. */
    private final AtomicReference<BaseFont> arabicBaseFontCache = new AtomicReference<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public byte[] generateInvoicePdf(Invoice invoice, String language) {
        String lang = normalizeLanguage(language);
        Tenant tenant = invoice.getTenant();
        Font base = font(lang, 8f, Font.NORMAL, Color.BLACK);
        Font title = font(lang, 15f, Font.BOLD, INK);
        Font section = font(lang, 8.8f, Font.BOLD, Color.WHITE);
        Font label = font(lang, 7.5f, Font.BOLD, INK);
        Font value = font(lang, 7.5f, Font.NORMAL, Color.BLACK);
        Font small = font(lang, 6.6f, Font.NORMAL, new Color(100, 100, 100));
        Font totalFont = font(lang, 9f, Font.BOLD, INK);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 28, 28, 26, 26);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new FooterPageEvent(
                    footerNotes(tenant, lang), documentReference(invoice), small));
            document.open();

            addAgencyBlock(document, tenant, title, base, small);
            addInvoiceBlock(document, invoice, section, label, value, lang);
            addClientBlock(document, invoice, section, label, value, lang);

            if (invoice.getContract() != null) {
                addRentalContextBlock(document, invoice.getContract(), section, label, value, lang);
                addFinancialBreakdown(document, invoice, invoice.getContract(), section, label, value, totalFont, lang);
            } else {
                addSimpleAmountBlock(document, invoice, section, label, totalFont, lang);
            }

            document.close();
            log.info("[INVOICE_PDF_GENERATE] invoiceId={} lang={} hasContract={} success=true",
                    invoice.getId(), lang, invoice.getContract() != null);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("[INVOICE_PDF_GENERATE] Failed for invoiceId={}", invoice.getId(), e);
            throw new PdfGenerationException("Unable to generate invoice PDF", e);
        }
    }

    public byte[] generateInvoiceListPdf(List<Invoice> invoices, InvoiceExportFilter filter, Tenant tenant, String language) {
        String lang = normalizeLanguage(language);
        Font base = font(lang, 7.5f, Font.NORMAL, Color.BLACK);
        Font title = font(lang, 15f, Font.BOLD, INK);
        Font section = font(lang, 8.8f, Font.BOLD, Color.WHITE);
        Font label = font(lang, 7.2f, Font.BOLD, INK);
        Font small = font(lang, 6.6f, Font.NORMAL, new Color(100, 100, 100));
        Font tableHeader = font(lang, 6.6f, Font.BOLD, Color.WHITE);
        Font tableCell = font(lang, 6.6f, Font.NORMAL, Color.BLACK);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 22, 22, 26, 26);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new FooterPageEvent(footerNotes(tenant, lang), "INV-LIST-" + LocalDateTime.now(), small));
            document.open();

            addListCover(document, invoices, filter, tenant, title, base, section, label, lang);
            addListTable(document, invoices, section, tableHeader, tableCell, lang);

            document.close();
            log.info("[INVOICE_LIST_PDF_GENERATE] tenantId={} count={} lang={} success=true",
                    tenant != null ? tenant.getId() : null, invoices.size(), lang);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("[INVOICE_LIST_PDF_GENERATE] Failed for tenantId={}", tenant != null ? tenant.getId() : null, e);
            throw new PdfGenerationException("Unable to generate invoice list PDF", e);
        }
    }

    // ── Individual invoice: sections ─────────────────────────────────────────

    private void addAgencyBlock(Document document, Tenant tenant, Font title, Font base, Font small) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1f, 3f});

        PdfPCell logoCell = cell("", Rectangle.BOX, 5, Element.ALIGN_CENTER, LIGHT_BLUE, base);
        Image logo = agencyAssetImage(tenant != null ? tenant.getLogoUrl() : null);
        if (logo != null) {
            logo.scaleToFit(60, 45);
            logoCell.addElement(logo);
        } else {
            Paragraph mark = new Paragraph(initials(tenant != null ? tenant.getName() : null), title);
            mark.setAlignment(Element.ALIGN_CENTER);
            logoCell.addElement(mark);
        }
        header.addCell(logoCell);

        PdfPCell agencyCell = cell("", Rectangle.BOX, 6, Element.ALIGN_LEFT, Color.WHITE, base);
        agencyCell.addElement(new Paragraph(value(tenant != null ? tenant.getName() : null, "Agency"), title));
        String addressLine = join(tenant != null ? tenant.getAddress() : null, tenant != null ? tenant.getCity() : null);
        if (!addressLine.isBlank()) agencyCell.addElement(new Paragraph(addressLine, small));
        String contactLine = join(
                tenant != null && has(tenant.getPhone()) ? "Tel: " + tenant.getPhone() : null,
                tenant != null && has(tenant.getEmail()) ? "Email: " + tenant.getEmail() : null);
        if (!contactLine.isBlank()) agencyCell.addElement(new Paragraph(contactLine, small));
        if (tenant != null && has(tenant.getTaxId())) {
            agencyCell.addElement(new Paragraph("RC / ICE: " + tenant.getTaxId(), small));
        }
        header.addCell(agencyCell);
        document.add(header);
    }

    private void addInvoiceBlock(Document document, Invoice invoice, Font section, Font label, Font value, String lang) throws Exception {
        addSectionHeader(document, t(lang, "section.invoice"), section);
        PdfPTable table = kvTable();
        row(table, t(lang, "field.invoiceNumber"), value(invoice.getInvoiceNumber(), ""), label, value);
        row(table, t(lang, "field.issueDate"), formatDate(invoice.getIssueDate()), label, value);
        row(table, t(lang, "field.dueDate"), formatDate(invoice.getDueDate()), label, value);
        row(table, t(lang, "field.status"), statusLabel(invoice.getStatus(), lang), label, value);
        row(table, t(lang, "field.currency"), value(invoice.getCurrency(), "MAD"), label, value);
        document.add(table);

        Payment latestPayment = latestPaymentFor(invoice);
        if (latestPayment != null) {
            PdfPTable payTable = kvTable();
            row(payTable, t(lang, "field.paymentMethod"),
                    latestPayment.getPaymentMethod() != null ? latestPayment.getPaymentMethod().name() : "", label, value);
            if (has(latestPayment.getReference())) {
                row(payTable, t(lang, "field.paymentReference"), latestPayment.getReference(), label, value);
            }
            document.add(payTable);
        }
    }

    /** Latest linked Payment for this invoice, if any — the payment block is
     *  omitted entirely (never printed blank) when none exists yet. */
    private Payment latestPaymentFor(Invoice invoice) {
        if (invoice.getTenant() == null) return null;
        List<Payment> payments = paymentRepository.findAllByTenantIdAndInvoiceIdOrderByPaymentDateDesc(
                invoice.getTenant().getId(), invoice.getId());
        return payments.isEmpty() ? null : payments.get(0);
    }

    private void addClientBlock(Document document, Invoice invoice, Font section, Font label, Font value, String lang) throws Exception {
        addSectionHeader(document, t(lang, "section.client"), section);
        PdfPTable table = kvTable();
        if (invoice.getClient() != null) {
            var c = invoice.getClient();
            if (has(c.getName())) row(table, t(lang, "field.name"), c.getName(), label, value);
            if (has(c.getPhone())) row(table, t(lang, "field.phone"), c.getPhone(), label, value);
            if (has(c.getEmail())) row(table, t(lang, "field.email"), c.getEmail(), label, value);
            String address = join(c.getAddress(), c.getCity());
            if (!address.isBlank()) row(table, t(lang, "field.address"), address, label, value);
        } else if (has(invoice.getClientName())) {
            row(table, t(lang, "field.name"), invoice.getClientName(), label, value);
        }
        document.add(table);
    }

    private void addRentalContextBlock(Document document, Contract c, Font section, Font label, Font value, String lang) throws Exception {
        addSectionHeader(document, t(lang, "section.rental"), section);
        PdfPTable table = kvTable();
        row(table, t(lang, "field.contractNumber"), value(c.getContractNumber(), ""), label, value);
        String vehicle = join(c.getVehicleBrand(), c.getVehicleModel());
        if (!vehicle.isBlank()) row(table, t(lang, "field.vehicle"), vehicle, label, value);
        if (has(c.getVehicleRegistration())) row(table, t(lang, "field.plate"), c.getVehicleRegistration(), label, value);
        row(table, t(lang, "field.startDate"), formatDate(c.getStartDate()), label, value);
        row(table, t(lang, "field.endDate"), formatDate(c.getEndDate()), label, value);
        Integer days = rentalDays(c);
        if (days != null) row(table, t(lang, "field.days"), String.valueOf(days), label, value);
        if (c.getDailyPrice() != null) {
            row(table, t(lang, "field.dailyRate"), formatCurrency(c.getDailyPrice(), currencyOf(c), lang), label, value);
        }
        document.add(table);
    }

    private void addFinancialBreakdown(Document document, Invoice invoice, Contract c,
                                        Font section, Font label, Font value, Font totalFont, String lang) throws Exception {
        addSectionHeader(document, t(lang, "section.financial"), section);
        String currency = currencyOf(c);
        Integer days = rentalDays(c);
        BigDecimal subtotal = safe(c.getDailyPrice()).multiply(BigDecimal.valueOf(days != null ? days : 0));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.4f, 1f});

        amountRow(table, t(lang, "field.subtotal"), subtotal, currency, lang, label, value, false);

        BigDecimal extraFees = sum(c.getDeliveryFees(), c.getReturnFees(), c.getLateFees(), c.getCleaningFees(), c.getFuelCharges());
        if (c.getDeliveryFees() != null && c.getDeliveryFees().signum() > 0) {
            amountRow(table, t(lang, "field.deliveryFees"), c.getDeliveryFees(), currency, lang, label, value, false);
        }
        if (c.getReturnFees() != null && c.getReturnFees().signum() > 0) {
            amountRow(table, t(lang, "field.returnFees"), c.getReturnFees(), currency, lang, label, value, false);
        }
        if (c.getLateFees() != null && c.getLateFees().signum() > 0) {
            amountRow(table, t(lang, "field.lateFees"), c.getLateFees(), currency, lang, label, value, false);
        }
        if (c.getCleaningFees() != null && c.getCleaningFees().signum() > 0) {
            amountRow(table, t(lang, "field.cleaningFees"), c.getCleaningFees(), currency, lang, label, value, false);
        }
        if (c.getFuelCharges() != null && c.getFuelCharges().signum() > 0) {
            amountRow(table, t(lang, "field.fuelCharges"), c.getFuelCharges(), currency, lang, label, value, false);
        }
        if (c.getDiscountAmount() != null && c.getDiscountAmount().signum() > 0) {
            amountRow(table, t(lang, "field.discount"), c.getDiscountAmount().negate(), currency, lang, label, value, false);
        }
        if (c.getTaxAmount() != null && c.getTaxAmount().signum() > 0) {
            amountRow(table, t(lang, "field.tax"), c.getTaxAmount(), currency, lang, label, value, false);
        }
        if (c.getDepositAmount() != null && c.getDepositAmount().signum() > 0) {
            amountRow(table, t(lang, "field.deposit"),
                    c.getDepositAmount(), value(c.getDepositCurrency(), currency), lang, label, value, false);
        }
        if (c.getPaidAmount() != null) {
            amountRow(table, t(lang, "field.paidAmount"), c.getPaidAmount(), currency, lang, label, value, false);
        }
        if (c.getRemainingAmount() != null) {
            amountRow(table, t(lang, "field.remaining"), c.getRemainingAmount(), currency, lang, label, value, false);
        }

        BigDecimal grandTotal = c.getTotalPrice() != null ? c.getTotalPrice() : subtotal.add(extraFees);
        amountRow(table, t(lang, "field.grandTotal"), grandTotal, currency, lang, totalFont, totalFont, true);

        document.add(table);
    }

    private void addSimpleAmountBlock(Document document, Invoice invoice, Font section, Font label, Font totalFont, String lang) throws Exception {
        addSectionHeader(document, t(lang, "section.amount"), section);
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.4f, 1f});
        amountRow(table, t(lang, "field.grandTotal"), invoice.getAmount(), value(invoice.getCurrency(), "MAD"), lang, totalFont, totalFont, true);
        document.add(table);
    }

    // ── Invoice list report ──────────────────────────────────────────────────

    private void addListCover(Document document, List<Invoice> invoices, InvoiceExportFilter filter, Tenant tenant,
                               Font title, Font base, Font section, Font label, String lang) throws Exception {
        Paragraph titleP = new Paragraph(t(lang, "list.title"), title);
        titleP.setAlignment(Element.ALIGN_CENTER);
        document.add(titleP);

        Paragraph agency = new Paragraph(tenant != null ? value(tenant.getName(), "") : "", base);
        agency.setAlignment(Element.ALIGN_CENTER);
        agency.setSpacingBefore(6);
        document.add(agency);

        String period = (filter != null && (filter.getDateFrom() != null || filter.getDateTo() != null))
                ? (filter.getDateFrom() != null ? formatDate(filter.getDateFrom()) : "…") + " – "
                        + (filter.getDateTo() != null ? formatDate(filter.getDateTo()) : "…")
                : t(lang, "list.allDates");
        Paragraph periodP = new Paragraph(t(lang, "list.period") + ": " + period, base);
        periodP.setAlignment(Element.ALIGN_CENTER);
        periodP.setSpacingBefore(4);
        document.add(periodP);

        Paragraph generated = new Paragraph(t(lang, "list.generated") + ": " + LocalDateTime.now().format(DATETIME), base);
        generated.setAlignment(Element.ALIGN_CENTER);
        generated.setSpacingBefore(4);
        document.add(generated);

        String activeFilters = describeActiveFilters(filter, lang);
        if (!activeFilters.isBlank()) {
            Paragraph filtersP = new Paragraph(t(lang, "list.filters") + ": " + activeFilters, base);
            filtersP.setAlignment(Element.ALIGN_CENTER);
            filtersP.setSpacingBefore(4);
            document.add(filtersP);
        }

        addSectionHeader(document, t(lang, "list.totals"), section);
        Map<String, BigDecimal> totalsByStatus = new LinkedHashMap<>();
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (Invoice inv : invoices) {
            String key = inv.getStatus() != null ? inv.getStatus().name() : "UNKNOWN";
            totalsByStatus.merge(key, safe(inv.getAmount()), BigDecimal::add);
            countByStatus.merge(key, 1L, Long::sum);
        }
        BigDecimal grandTotal = totalsByStatus.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        PdfPTable totalsTable = kvTable();
        row(totalsTable, t(lang, "list.count"), String.valueOf(invoices.size()), label, base);
        row(totalsTable, t(lang, "list.invoicedTotal"), formatCurrency(grandTotal, "MAD", lang), label, base);
        for (var entry : totalsByStatus.entrySet()) {
            InvoiceStatus status = parseStatus(entry.getKey());
            String statusText = status != null ? statusLabel(status, lang) : entry.getKey();
            row(totalsTable, statusText + " (" + countByStatus.get(entry.getKey()) + ")",
                    formatCurrency(entry.getValue(), "MAD", lang), label, base);
        }
        document.add(totalsTable);
    }

    private void addListTable(Document document, List<Invoice> invoices, Font section, Font tableHeader, Font tableCell, String lang) throws Exception {
        addSectionHeader(document, t(lang, "list.tableTitle"), section);
        PdfPTable table = new PdfPTable(9);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.3f, 1.4f, 1.1f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f});
        table.setHeaderRows(1);

        String[] headers = {
                t(lang, "col.invoiceNumber"), t(lang, "col.client"), t(lang, "col.contractNumber"),
                t(lang, "col.issueDate"), t(lang, "col.dueDate"), t(lang, "col.amount"),
                t(lang, "col.paid"), t(lang, "col.remaining"), t(lang, "col.status")
        };
        for (String h : headers) {
            table.addCell(cell(h, Rectangle.BOX, 4, Element.ALIGN_CENTER, BLUE, tableHeader));
        }

        for (Invoice inv : invoices) {
            BigDecimal amount = safe(inv.getAmount());
            BigDecimal paid = paidAmountFor(inv);
            BigDecimal remaining = amount.subtract(paid).max(BigDecimal.ZERO);

            table.addCell(cell(value(inv.getInvoiceNumber(), ""), Rectangle.BOX, 3, Element.ALIGN_LEFT, Color.WHITE, tableCell));
            table.addCell(cell(clientDisplayName(inv), Rectangle.BOX, 3, Element.ALIGN_LEFT, Color.WHITE, tableCell));
            table.addCell(cell(inv.getContract() != null ? value(inv.getContract().getContractNumber(), "—") : "—",
                    Rectangle.BOX, 3, Element.ALIGN_LEFT, Color.WHITE, tableCell));
            table.addCell(cell(formatDate(inv.getIssueDate()), Rectangle.BOX, 3, Element.ALIGN_CENTER, Color.WHITE, tableCell));
            table.addCell(cell(formatDate(inv.getDueDate()), Rectangle.BOX, 3, Element.ALIGN_CENTER, Color.WHITE, tableCell));
            table.addCell(cell(formatCurrency(amount, value(inv.getCurrency(), "MAD"), lang), Rectangle.BOX, 3, Element.ALIGN_RIGHT, Color.WHITE, tableCell));
            table.addCell(cell(formatCurrency(paid, value(inv.getCurrency(), "MAD"), lang), Rectangle.BOX, 3, Element.ALIGN_RIGHT, Color.WHITE, tableCell));
            table.addCell(cell(formatCurrency(remaining, value(inv.getCurrency(), "MAD"), lang), Rectangle.BOX, 3, Element.ALIGN_RIGHT, Color.WHITE, tableCell));
            table.addCell(cell(statusLabel(inv.getStatus(), lang), Rectangle.BOX, 3, Element.ALIGN_CENTER, Color.WHITE, tableCell));
        }
        document.add(table);
    }

    private String clientDisplayName(Invoice invoice) {
        if (invoice.getClient() != null && has(invoice.getClient().getName())) return invoice.getClient().getName();
        return value(invoice.getClientName(), "");
    }

    /** Paid amount derivation: sum of PAID/PARTIALLY_PAID Payment rows linked
     *  to this invoice (the authoritative source of truth — never a
     *  denormalized override field). Falls back to the full invoice amount
     *  when the invoice itself is PAID but has no linked Payment rows yet
     *  (matching markAsPaid()'s convention), else zero. Public — also reused
     *  by PlatformEmailService#sendInvoicePdfEmail to build the "amount
     *  paid/remaining" email summary without duplicating this logic. */
    public BigDecimal paidAmountFor(Invoice invoice) {
        if (invoice.getTenant() != null) {
            BigDecimal fromPayments = paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(
                    invoice.getTenant().getId(), invoice.getId());
            if (fromPayments != null && fromPayments.signum() > 0) return fromPayments;
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) return safe(invoice.getAmount());
        if (invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID && invoice.getContract() != null
                && invoice.getContract().getPaidAmount() != null) {
            return invoice.getContract().getPaidAmount();
        }
        return BigDecimal.ZERO;
    }

    private String describeActiveFilters(InvoiceExportFilter filter, String lang) {
        if (filter == null) return "";
        StringBuilder sb = new StringBuilder();
        if (has(filter.getSearch())) appendFilter(sb, t(lang, "filter.search") + "=" + filter.getSearch());
        if (has(filter.getStatus()) && !"all".equalsIgnoreCase(filter.getStatus())) appendFilter(sb, t(lang, "filter.status") + "=" + filter.getStatus());
        if (filter.getClientId() != null) appendFilter(sb, t(lang, "filter.client") + "=#" + filter.getClientId());
        if (filter.getContractId() != null) appendFilter(sb, t(lang, "filter.contract") + "=#" + filter.getContractId());
        if (filter.getVehicleId() != null) appendFilter(sb, t(lang, "filter.vehicle") + "=#" + filter.getVehicleId());
        return sb.toString();
    }

    private void appendFilter(StringBuilder sb, String text) {
        if (!sb.isEmpty()) sb.append(" · ");
        sb.append(text);
    }

    // ── Currency / money formatting ──────────────────────────────────────────

    /**
     * Formats a BigDecimal amount for display in FR/EN/AR. Money is rounded to
     * exactly 2 decimals with HALF_UP only at this formatting boundary — never
     * anywhere calculations happen — and BigDecimal is used end-to-end (never
     * double/float).
     *
     * <p>Digit choice for Arabic: Latin (ASCII) digits are used deliberately
     * rather than Eastern Arabic-Indic digits (٠١٢...) — this is a business
     * financial document where Latin numerals remain the safer, more broadly
     * legible default across Arabic-speaking markets (including Morocco,
     * this project's primary market, where Latin digits are the commercial
     * norm), while labels/currency and layout still honor the Arabic locale.
     */
    public String formatCurrency(BigDecimal amount, String currency, String language) {
        BigDecimal rounded = safe(amount).setScale(2, RoundingMode.HALF_UP);
        String cur = has(currency) ? currency : "MAD";
        String lang = normalizeLanguage(language);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        String formattedNumber;
        switch (lang) {
            case "fr" -> {
                symbols.setGroupingSeparator(' '); // narrow/no-break space
                symbols.setDecimalSeparator(',');
                DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
                formattedNumber = df.format(rounded);
                return formattedNumber + " " + cur;
            }
            case "ar" -> {
                symbols.setGroupingSeparator(',');
                symbols.setDecimalSeparator('.');
                DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
                formattedNumber = df.format(rounded);
                return cur + " " + formattedNumber;
            }
            default -> { // en
                symbols.setGroupingSeparator(',');
                symbols.setDecimalSeparator('.');
                DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
                formattedNumber = df.format(rounded);
                return cur + " " + formattedNumber;
            }
        }
    }

    private void amountRow(PdfPTable table, String labelText, BigDecimal amount, String currency, String lang,
                           Font labelFont, Font valueFont, boolean emphasize) {
        PdfPCell labelCell = new PdfPCell(new Phrase(labelText, labelFont));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(BORDER);
        labelCell.setPadding(4);
        labelCell.setBackgroundColor(emphasize ? LIGHT_BLUE : Color.WHITE);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatCurrency(amount, currency, lang), valueFont));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(BORDER);
        valueCell.setPadding(4);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(emphasize ? LIGHT_BLUE : Color.WHITE);
        table.addCell(valueCell);
    }

    // ── Status labels ─────────────────────────────────────────────────────────

    private static final Map<InvoiceStatus, Map<String, String>> STATUS_LABELS = new LinkedHashMap<>();
    static {
        put(InvoiceStatus.DRAFT, "Brouillon", "Draft", "مسودة");
        put(InvoiceStatus.ISSUED, "Émise", "Issued", "صادرة");
        put(InvoiceStatus.PENDING, "En attente", "Pending", "معلقة");
        put(InvoiceStatus.PARTIALLY_PAID, "Partiellement payée", "Partially paid", "مدفوعة جزئياً");
        put(InvoiceStatus.PAID, "Payée", "Paid", "مدفوعة");
        put(InvoiceStatus.OVERDUE, "En retard", "Overdue", "متأخرة");
        put(InvoiceStatus.CANCELLED, "Annulée", "Cancelled", "ملغاة");
        put(InvoiceStatus.REFUNDED, "Remboursée", "Refunded", "مستردة");
    }

    private static void put(InvoiceStatus status, String fr, String en, String ar) {
        STATUS_LABELS.put(status, Map.of("fr", fr, "en", en, "ar", ar));
    }

    public String statusLabel(InvoiceStatus status, String language) {
        if (status == null) return "";
        Map<String, String> byLang = STATUS_LABELS.get(status);
        if (byLang == null) return status.name();
        return byLang.getOrDefault(normalizeLanguage(language), status.name());
    }

    private InvoiceStatus parseStatus(String name) {
        try {
            return InvoiceStatus.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Section / footer labels (FR/EN/AR) ────────────────────────────────────

    private static final Map<String, Map<String, String>> LABELS = new LinkedHashMap<>();
    static {
        label("section.invoice", "FACTURE", "INVOICE", "الفاتورة");
        label("section.client", "CLIENT", "CLIENT", "العميل");
        label("section.rental", "CONTEXTE DE LOCATION", "RENTAL CONTEXT", "سياق الإيجار");
        label("section.financial", "DÉTAIL FINANCIER", "FINANCIAL BREAKDOWN", "التفاصيل المالية");
        label("section.amount", "MONTANT", "AMOUNT", "المبلغ");

        label("field.invoiceNumber", "N° Facture", "Invoice No.", "رقم الفاتورة");
        label("field.issueDate", "Date d'émission", "Issue Date", "تاريخ الإصدار");
        label("field.dueDate", "Date d'échéance", "Due Date", "تاريخ الاستحقاق");
        label("field.status", "Statut", "Status", "الحالة");
        label("field.currency", "Devise", "Currency", "العملة");
        label("field.paymentMethod", "Mode de paiement", "Payment Method", "طريقة الدفع");
        label("field.paymentReference", "Référence", "Reference", "المرجع");
        label("field.name", "Nom", "Name", "الاسم");
        label("field.phone", "Téléphone", "Phone", "الهاتف");
        label("field.email", "E-mail", "Email", "البريد الإلكتروني");
        label("field.address", "Adresse", "Address", "العنوان");
        label("field.contractNumber", "N° Contrat", "Contract No.", "رقم العقد");
        label("field.vehicle", "Véhicule", "Vehicle", "المركبة");
        label("field.plate", "Immatriculation", "Plate", "رقم اللوحة");
        label("field.startDate", "Date de départ", "Start Date", "تاريخ البدء");
        label("field.endDate", "Date de retour", "End Date", "تاريخ الانتهاء");
        label("field.days", "Nombre de jours", "Number of Days", "عدد الأيام");
        label("field.dailyRate", "Tarif journalier", "Daily Rate", "السعر اليومي");
        label("field.subtotal", "Sous-total", "Subtotal", "المجموع الفرعي");
        label("field.deliveryFees", "Frais de livraison", "Delivery Fees", "رسوم التوصيل");
        label("field.returnFees", "Frais de reprise", "Return Fees", "رسوم الإرجاع");
        label("field.lateFees", "Frais de retard", "Late Fees", "رسوم التأخير");
        label("field.cleaningFees", "Frais de nettoyage", "Cleaning Fees", "رسوم التنظيف");
        label("field.fuelCharges", "Frais de carburant", "Fuel Charges", "رسوم الوقود");
        label("field.discount", "Remise", "Discount", "الخصم");
        label("field.tax", "Taxe", "Tax", "الضريبة");
        label("field.deposit", "Caution", "Deposit", "التأمين");
        label("field.paidAmount", "Montant payé", "Amount Paid", "المبلغ المدفوع");
        label("field.remaining", "Reste à payer", "Remaining Balance", "المبلغ المتبقي");
        label("field.grandTotal", "TOTAL GÉNÉRAL", "GRAND TOTAL", "المجموع الكلي");

        label("list.title", "Rapport des Factures", "Invoice Report", "تقرير الفواتير");
        label("list.period", "Période", "Period", "الفترة");
        label("list.allDates", "Toutes les dates", "All dates", "جميع التواريخ");
        label("list.generated", "Généré le", "Generated on", "تم الإنشاء في");
        label("list.filters", "Filtres actifs", "Active Filters", "المرشحات النشطة");
        label("list.totals", "TOTAUX", "TOTALS", "المجاميع");
        label("list.count", "Nombre de factures", "Invoice Count", "عدد الفواتير");
        label("list.invoicedTotal", "Total facturé", "Total Invoiced", "إجمالي الفوترة");
        label("list.tableTitle", "DÉTAIL DES FACTURES", "INVOICE DETAILS", "تفاصيل الفواتير");

        label("col.invoiceNumber", "N° Facture", "Invoice No.", "رقم الفاتورة");
        label("col.client", "Client", "Client", "العميل");
        label("col.contractNumber", "N° Contrat", "Contract No.", "رقم العقد");
        label("col.issueDate", "Émission", "Issued", "الإصدار");
        label("col.dueDate", "Échéance", "Due", "الاستحقاق");
        label("col.amount", "Montant", "Amount", "المبلغ");
        label("col.paid", "Payé", "Paid", "مدفوع");
        label("col.remaining", "Reste", "Remaining", "المتبقي");
        label("col.status", "Statut", "Status", "الحالة");

        label("filter.search", "Recherche", "Search", "بحث");
        label("filter.status", "Statut", "Status", "الحالة");
        label("filter.client", "Client", "Client", "العميل");
        label("filter.contract", "Contrat", "Contract", "العقد");
        label("filter.vehicle", "Véhicule", "Vehicle", "المركبة");

        label("footer.generatedOn", "Généré le", "Generated on", "تم الإنشاء في");
    }

    private static void label(String key, String fr, String en, String ar) {
        LABELS.put(key, Map.of("fr", fr, "en", en, "ar", ar));
    }

    public String t(String language, String key) {
        Map<String, String> byLang = LABELS.get(key);
        if (byLang == null) return key;
        return byLang.getOrDefault(normalizeLanguage(language), byLang.get("en"));
    }

    // ── Footer with page numbers (mirrors PdfService/ReportPdfGenerator pattern) ──

    private class FooterPageEvent extends PdfPageEventHelper {
        private final String notes;
        private final String reference;
        private final Font font;

        FooterPageEvent(String notes, String reference, Font font) {
            this.notes = notes;
            this.reference = reference;
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfPTable footer = new PdfPTable(2);
                footer.setTotalWidth(document.right() - document.left());
                footer.setWidths(new float[]{3f, 1f});

                Paragraph left = new Paragraph();
                if (has(notes)) left.add(new Chunk(notes + "\n", font));
                left.add(new Chunk(reference, font));
                PdfPCell leftCell = new PdfPCell(left);
                leftCell.setBorder(Rectangle.TOP);
                leftCell.setBorderColor(BORDER);
                leftCell.setPadding(4);
                footer.addCell(leftCell);

                PdfPCell rightCell = new PdfPCell(new Phrase(
                        "Page " + writer.getPageNumber(), font));
                rightCell.setBorder(Rectangle.TOP);
                rightCell.setBorderColor(BORDER);
                rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                rightCell.setPadding(4);
                footer.addCell(rightCell);

                footer.writeSelectedRows(0, -1, document.left(), document.bottom() - 4, writer.getDirectContent());
            } catch (Exception e) {
                log.warn("[INVOICE_PDF_FOOTER] Failed to render footer", e);
            }
        }
    }

    private String footerNotes(Tenant tenant, String lang) {
        if (tenant != null && has(tenant.getTermsAndConditions())) {
            String terms = tenant.getTermsAndConditions().trim();
            return terms.length() > 160 ? terms.substring(0, 160) + "…" : terms;
        }
        return t(lang, "footer.generatedOn") + ": " + LocalDateTime.now().format(DATETIME);
    }

    private String documentReference(Invoice invoice) {
        return value(invoice.getInvoiceNumber(), "INV") + " • " + LocalDateTime.now().format(DATETIME);
    }

    // ── Arabic font loading (classpath, embedded, cached) ────────────────────

    private Font font(String language, float size, int style, Color color) {
        if ("ar".equals(language)) {
            BaseFont arabic = arabicBaseFont();
            if (arabic != null) return new Font(arabic, size, style, color);
        }
        return new Font(Font.HELVETICA, size, style, color);
    }

    /**
     * Loads and caches the bundled Noto Naskh Arabic TTF from the classpath
     * (never a filesystem path — see class javadoc). Returns null (falls back
     * to Helvetica, which cannot render Arabic glyphs) only if the bundled
     * resource is somehow missing/corrupt, which should never happen in a
     * correctly built jar.
     */
    private BaseFont arabicBaseFont() {
        BaseFont cached = arabicBaseFontCache.get();
        if (cached != null) return cached;
        synchronized (arabicBaseFontCache) {
            cached = arabicBaseFontCache.get();
            if (cached != null) return cached;
            try (InputStream in = getClass().getResourceAsStream(ARABIC_FONT_RESOURCE)) {
                if (in == null) {
                    log.error("[INVOICE_PDF_ARABIC_FONT] Bundled resource {} not found on classpath — "
                            + "Arabic PDFs will fall back to Helvetica (no Arabic glyph support).", ARABIC_FONT_RESOURCE);
                    return null;
                }
                byte[] ttfBytes = in.readAllBytes();
                BaseFont baseFont = BaseFont.createFont(ARABIC_FONT_NAME, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                        true, ttfBytes, null);
                arabicBaseFontCache.set(baseFont);
                log.info("[INVOICE_PDF_ARABIC_FONT] Loaded and cached embedded Arabic font from classpath");
                return baseFont;
            } catch (Exception e) {
                log.error("[INVOICE_PDF_ARABIC_FONT] Failed to load bundled Arabic font", e);
                return null;
            }
        }
    }

    // ── Shared cell/table helpers ─────────────────────────────────────────────

    private void addSectionHeader(Document document, String titleText, Font sectionFont) throws Exception {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setSpacingBefore(8);
        header.addCell(cell(titleText, Rectangle.BOX, 5, Element.ALIGN_LEFT, BLUE, sectionFont));
        document.add(header);
    }

    private PdfPTable kvTable() {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 2f});
        return table;
    }

    private void row(PdfPTable table, String labelText, String valueText, Font labelFont, Font valueFont) {
        table.addCell(cell(labelText, Rectangle.BOX, 4, Element.ALIGN_LEFT, LIGHT_BLUE, labelFont));
        table.addCell(cell(value(valueText, ""), Rectangle.BOX, 4, Element.ALIGN_LEFT, Color.WHITE, valueFont));
    }

    private PdfPCell cell(String text, int border, float padding, int align, Color bg, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value(text, ""), font));
        cell.setBorder(border);
        cell.setBorderColor(BORDER);
        cell.setPadding(padding);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(bg);
        return cell;
    }

    private Image agencyAssetImage(String source) {
        if (source == null || source.isBlank()) return null;
        try {
            byte[] bytes;
            if (source.startsWith("data:") && source.contains(",")) {
                bytes = Base64.getDecoder().decode(source.substring(source.indexOf(",") + 1));
            } else if (source.startsWith("http://") || source.startsWith("https://")) {
                java.net.URL url = new java.net.URL(source);
                java.net.URLConnection connection = url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                try (var in = connection.getInputStream()) {
                    bytes = in.readAllBytes();
                }
            } else {
                Path localPath = source.startsWith("/uploads/") ? Path.of(source.substring(1)) : Paths.get(source);
                if (Files.exists(localPath)) {
                    bytes = Files.readAllBytes(localPath);
                } else {
                    bytes = Base64.getDecoder().decode(source);
                }
            }
            return Image.getInstance(bytes);
        } catch (Exception e) {
            log.warn("[INVOICE_PDF_LOGO] Failed to load agency logo: {}", e.getMessage());
            return null;
        }
    }

    // ── Small utilities ────────────────────────────────────────────────────────

    private String normalizeLanguage(String language) {
        if (language == null) return "fr";
        String lower = language.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "ar", "en" -> lower;
            default -> "fr";
        };
    }

    private Integer rentalDays(Contract c) {
        if (c.getRentalDays() != null) return c.getRentalDays();
        if (c.getStartDate() != null && c.getEndDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(c.getStartDate(), c.getEndDate());
            return (int) Math.max(days, 1);
        }
        return null;
    }

    private String currencyOf(Contract c) {
        return "MAD";
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) total = total.add(safe(v));
        return total;
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE) : "";
    }

    private boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private String value(String v, String fallback) {
        return has(v) ? v.trim() : fallback;
    }

    private String join(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (has(v)) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(v.trim());
            }
        }
        return sb.toString();
    }

    private String initials(String name) {
        String v = value(name, "IN");
        String[] words = v.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) sb.append(Character.toUpperCase(w.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.isEmpty() ? "IN" : sb.toString();
    }
}
