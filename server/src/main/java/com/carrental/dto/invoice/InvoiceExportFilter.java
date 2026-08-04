package com.carrental.dto.invoice;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Filter payload shared by the invoice PDF export, CSV export, and (in a
 * future pass) any list-preview endpoint. Tenant scoping is NEVER carried in
 * this DTO — {@code InvoiceService}/{@code InvoiceRepository} always add
 * {@code tenant_id = TenantContext.getCurrentTenantId()} server-side,
 * regardless of anything sent here.
 */
@Data
public class InvoiceExportFilter {

    /** Free-text search across invoice number / client name. */
    private String search;

    /** {@link com.carrental.entity.InvoiceStatus} name, or null/"all" for no status filter. */
    private String status;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    private Long clientId;

    private Long contractId;

    /** Matches invoice.contract.vehicle.id — invoices without a linked contract never match. */
    private Long vehicleId;

    /** e.g. "issueDate,desc" — defaults to issueDate desc when blank. */
    private String sort;

    /** "ALL" (default) or "CURRENT_PAGE". */
    private String scope;

    /** Only consulted when scope == CURRENT_PAGE. */
    private List<Long> pageInvoiceIds;
}
