package com.carrental.dto.invoice;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only invoice projection returned by all invoice endpoints.
 */
@Data
@Builder
public class InvoiceResponse {

    private Long          id;
    private String        invoiceNumber;
    private String        clientName;
    private LocalDate     issueDate;
    private LocalDate     dueDate;
    private BigDecimal    amount;
    private InvoiceStatus status;
    private Long          tenantId;

    // ── Static factory ───────────────────────────────────────────────────────

    public static InvoiceResponse from(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .clientName(invoice.getClientName())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .tenantId(invoice.getTenant().getId())
                .build();
    }
}
