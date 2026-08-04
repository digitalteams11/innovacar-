package com.carrental.controller;

import com.carrental.dto.invoice.CreateInvoiceRequest;
import com.carrental.dto.invoice.UpdateInvoiceRequest;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.dto.invoice.InvoiceExportFilter;
import com.carrental.entity.Invoice;
import com.carrental.service.InvoicePdfService;
import com.carrental.service.InvoiceService;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SmtpMailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Invoice-management REST controller.
 *
 * <pre>
 * GET    /api/invoices                    – list all invoices                 [authenticated]
 * GET    /api/invoices/{id}                – get invoice by id                 [authenticated]
 * POST   /api/invoices                    – create invoice                    [ADMIN]
 * PUT    /api/invoices/{id}                – partial update                    [ADMIN]
 * PATCH  /api/invoices/{id}/pay             – mark invoice as paid              [ADMIN]
 * DELETE /api/invoices/{id}                – delete invoice                    [ADMIN]
 * GET    /api/invoices/{id}/pdf             – individual invoice PDF           [INVOICE_PDF_DOWNLOAD / INVOICE_VIEW]
 * POST   /api/invoices/export/pdf           – filtered invoice-list PDF        [INVOICE_EXPORT]
 * GET    /api/invoices/export/csv           – filtered invoice-list CSV        [INVOICE_EXPORT]
 * POST   /api/invoices/{id}/email           – email the invoice PDF to client  [INVOICE_EMAIL_SEND]
 * </pre>
 *
 * All endpoints sit behind the {@code JwtAuthenticationFilter} — an invalid or
 * missing token will never reach the controller.
 */
@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final PlatformEmailService platformEmailService;

    // ── GET /api/invoices ────────────────────────────────────────────────────

    /**
     * Returns all invoices in the caller's tenant.
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> listInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    // ── GET /api/invoices/{id} ───────────────────────────────────────────────

    /**
     * Fetches a single invoice. Returns 404 for invoices belonging to other
     * tenants (prevents cross-tenant enumeration).
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    // ── POST /api/invoices ───────────────────────────────────────────────────

    /**
     * Registers a new invoice in the caller's tenant. ADMIN-only.
     */
    @PostMapping
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.createInvoice(request));
    }

    // ── PUT /api/invoices/{id} ───────────────────────────────────────────────

    /**
     * Partially updates an invoice. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<InvoiceResponse> updateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, request));
    }

    // ── PATCH /api/invoices/{id}/pay ─────────────────────────────────────────

    /**
     * Marks an invoice as PAID. ADMIN-only.
     */
    @PatchMapping("/{id}/pay")
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<InvoiceResponse> markAsPaid(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.markAsPaid(id));
    }

    // ── DELETE /api/invoices/{id} ────────────────────────────────────────────

    /**
     * Hard-deletes an invoice. ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/invoices/{id}/pdf ───────────────────────────────────────────

    /**
     * Generates and returns the individual invoice PDF. {@code mode=inline}
     * (preview) only requires {@code INVOICE_VIEW}; the default
     * {@code mode=attachment} (actual download) requires the stronger
     * {@code INVOICE_PDF_DOWNLOAD}. Tenant isolation via
     * {@link InvoiceService#getInvoiceEntityById} — a cross-tenant id yields
     * a 404, never a 403, so tenant B can never confirm tenant A's id exists.
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("@rolePermissionService.has('INVOICE_PDF_DOWNLOAD') or @rolePermissionService.has('INVOICE_VIEW')")
    public ResponseEntity<byte[]> downloadInvoicePdf(
            @PathVariable Long id,
            @RequestParam(defaultValue = "attachment") String mode,
            @RequestParam(defaultValue = "fr") String lang) {
        Invoice invoice = invoiceService.getInvoiceEntityById(id);
        byte[] pdf = invoicePdfService.generateInvoicePdf(invoice, lang);
        invoiceService.markPdfGenerated(id, lang);

        String disposition = "inline".equalsIgnoreCase(mode) ? "inline" : "attachment";
        String filename = "facture-" + safeInvoiceNumber(invoice) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    // ── POST /api/invoices/export/pdf ────────────────────────────────────────

    /**
     * Filtered invoice-list PDF report. Empty result set → 422 with the
     * structured {@code NO_MATCHING_INVOICES} code (never a blank PDF).
     */
    @PostMapping("/export/pdf")
    @PreAuthorize("@rolePermissionService.has('INVOICE_EXPORT')")
    public ResponseEntity<?> exportInvoicesPdf(
            @RequestBody(required = false) InvoiceExportFilter filter,
            @RequestParam(defaultValue = "fr") String lang) {
        List<Invoice> invoices = invoiceService.exportFilteredInvoices(filter);
        if (invoices.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("code", "NO_MATCHING_INVOICES"));
        }
        var tenant = invoices.get(0).getTenant();
        byte[] pdf = invoicePdfService.generateInvoiceListPdf(invoices, filter, tenant, lang);
        String filename = "factures-" + exportPeriodLabel(filter) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    // ── GET /api/invoices/export/csv ─────────────────────────────────────────

    /**
     * Secondary CSV export, sharing {@link InvoiceService#exportFilteredInvoices}
     * with the PDF export so the two formats can never disagree on which
     * invoices matched. Query params mirror {@link InvoiceExportFilter}.
     */
    @GetMapping("/export/csv")
    @PreAuthorize("@rolePermissionService.has('INVOICE_EXPORT')")
    public ResponseEntity<?> exportInvoicesCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) String sort) {
        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setSearch(search);
        filter.setStatus(status);
        filter.setDateFrom(dateFrom);
        filter.setDateTo(dateTo);
        filter.setClientId(clientId);
        filter.setContractId(contractId);
        filter.setVehicleId(vehicleId);
        filter.setSort(sort);

        List<Invoice> invoices = invoiceService.exportFilteredInvoices(filter);
        if (invoices.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("code", "NO_MATCHING_INVOICES"));
        }

        byte[] csv = buildCsv(invoices);
        String filename = "factures-" + exportPeriodLabel(filter) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    // ── POST /api/invoices/{id}/email ────────────────────────────────────────

    /**
     * Emails the invoice PDF to the client. {@code toEmail} defaults to the
     * invoice's linked client email (or denormalized client name has no
     * email — 422 if none is resolvable). On confirmed provider success,
     * {@code PlatformEmailService} stamps {@code emailedAt}/{@code emailedTo}
     * itself; on failure the invoice is left untouched and the precise error
     * is returned here (never a generic toast).
     */
    @PostMapping("/{id}/email")
    @PreAuthorize("@rolePermissionService.has('INVOICE_EMAIL_SEND')")
    public ResponseEntity<?> emailInvoicePdf(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(defaultValue = "fr") String lang) {
        Invoice invoice = invoiceService.getInvoiceEntityById(id);

        String toEmail = body != null ? body.get("toEmail") : null;
        if (!StringUtils.hasText(toEmail)) {
            toEmail = invoice.getClient() != null ? invoice.getClient().getEmail() : null;
        }
        if (!StringUtils.hasText(toEmail)) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", "INVOICE_EMAIL_ADDRESS_MISSING",
                    "message", "No recipient email available for this invoice."));
        }

        SmtpMailService.SmtpResult result = platformEmailService.sendInvoicePdfEmail(invoice, toEmail, lang);
        if (result.sent()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "emailedTo", toEmail));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "success", false,
                "code", result.errorCode() != null ? result.errorCode() : "EMAIL_SEND_FAILED",
                "message", result.errorMessage() != null ? result.errorMessage() : "Unable to send the invoice email."));
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private String safeInvoiceNumber(Invoice invoice) {
        return StringUtils.hasText(invoice.getInvoiceNumber()) ? invoice.getInvoiceNumber() : ("INV-" + invoice.getId());
    }

    /** "yyyy-MM" from the filter's date range when present, else the current month. */
    private String exportPeriodLabel(InvoiceExportFilter filter) {
        LocalDate ref = filter != null && filter.getDateFrom() != null ? filter.getDateFrom() : LocalDate.now();
        return YearMonth.from(ref).toString();
    }

    /** RFC 4180 CSV — UTF-8 BOM, quoting, CRLF — matching the fleet-export convention. */
    private byte[] buildCsv(List<Invoice> invoices) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write('﻿');
            String[] headers = {"Invoice Number", "Client", "Contract Number", "Issue Date", "Due Date",
                    "Amount", "Paid", "Remaining", "Currency", "Status"};
            writeCsvRow(writer, headers);
            for (Invoice inv : invoices) {
                BigDecimal amount = inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO;
                BigDecimal paid = invoicePdfService.paidAmountFor(inv);
                BigDecimal remaining = amount.subtract(paid).max(BigDecimal.ZERO);
                String client = inv.getClient() != null && StringUtils.hasText(inv.getClient().getName())
                        ? inv.getClient().getName()
                        : (StringUtils.hasText(inv.getClientName()) ? inv.getClientName() : "");
                writeCsvRow(writer, new String[]{
                        safeInvoiceNumber(inv),
                        client,
                        inv.getContract() != null ? inv.getContract().getContractNumber() : "",
                        inv.getIssueDate() != null ? inv.getIssueDate().toString() : "",
                        inv.getDueDate() != null ? inv.getDueDate().toString() : "",
                        amount.toPlainString(),
                        paid.toPlainString(),
                        remaining.toPlainString(),
                        StringUtils.hasText(inv.getCurrency()) ? inv.getCurrency() : "MAD",
                        inv.getStatus() != null ? invoicePdfService.statusLabel(inv.getStatus(), "en") : ""
                });
            }
            writer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[INVOICE_CSV_EXPORT] Failed to build CSV", e);
            throw new IllegalStateException("Unable to generate the CSV export", e);
        }
    }

    private void writeCsvRow(Writer writer, String[] values) throws java.io.IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            writer.write(quoteCsv(values[i]));
        }
        writer.write("\r\n");
    }

    private String quoteCsv(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
