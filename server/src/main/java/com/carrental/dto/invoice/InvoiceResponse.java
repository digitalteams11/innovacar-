package com.carrental.dto.invoice;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only invoice projection returned by all invoice endpoints.
 */
@Data
@Builder
public class InvoiceResponse {

    private Long          id;
    private String        invoiceNumber;
    private String        clientName;
    private Long          clientId;
    private LocalDate     issueDate;
    private LocalDate     dueDate;
    private BigDecimal    amount;
    private InvoiceStatus status;
    private Long          tenantId;
    private String        currency;
    private Long          contractId;
    private String        contractNumber;
    // PDF/email metadata — lets the frontend hydrate "sent" state across
    // reloads instead of tracking it only in local session state.
    private LocalDateTime pdfGeneratedAt;
    private String        pdfLanguage;
    private boolean       pdfOutdated;
    private LocalDateTime emailedAt;
    private String        emailedTo;

    // ── Static factory ───────────────────────────────────────────────────────

    public static InvoiceResponse from(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .clientName(invoice.getClientName())
                .clientId(invoice.getClient() != null ? invoice.getClient().getId() : null)
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .tenantId(invoice.getTenant().getId())
                .currency(invoice.getCurrency())
                .contractId(invoice.getContract() != null ? invoice.getContract().getId() : null)
                .contractNumber(invoice.getContract() != null ? invoice.getContract().getContractNumber() : null)
                .pdfGeneratedAt(invoice.getPdfGeneratedAt())
                .pdfLanguage(invoice.getPdfLanguage())
                .pdfOutdated(invoice.isPdfOutdated())
                .emailedAt(invoice.getEmailedAt())
                .emailedTo(invoice.getEmailedTo())
                .build();
    }
}
