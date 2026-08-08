package com.carrental.dto.invoice;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceSourceType;
import com.carrental.entity.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private InvoiceSourceType sourceType;
    private String        notes;

    // ── Itemized breakdown ──────────────────────────────────────────────────
    private List<InvoiceLineResponse> lines;
    private BigDecimal    subtotalAmount;
    private BigDecimal    discountAmount;
    private BigDecimal    taxAmount;

    // ── Real financial state — never derived on the frontend ────────────────
    private BigDecimal    paidAmount;
    private BigDecimal    remainingAmount;

    // PDF/email metadata — lets the frontend hydrate "sent" state across
    // reloads instead of tracking it only in local session state.
    private LocalDateTime pdfGeneratedAt;
    private String        pdfLanguage;
    private boolean       pdfOutdated;
    private LocalDateTime emailedAt;
    private String        emailedTo;

    // ── Static factory ───────────────────────────────────────────────────────

    public static InvoiceResponse from(Invoice invoice) {
        return from(invoice, null, null);
    }

    /**
     * @param paidAmount      real collected amount (see PaymentService#collectedAmountFor);
     *                        pass null when the caller doesn't have it handy (falls back to 0).
     * @param remainingAmount amount - paidAmount, floored at 0; pass null to derive it here.
     */
    public static InvoiceResponse from(Invoice invoice, BigDecimal paidAmount, BigDecimal remainingAmount) {
        BigDecimal amount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
        BigDecimal paid = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        BigDecimal remaining = remainingAmount != null ? remainingAmount : amount.subtract(paid).max(BigDecimal.ZERO);

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
                .sourceType(invoice.getSourceType())
                .notes(invoice.getNotes())
                .lines(invoice.getLines() != null
                        ? invoice.getLines().stream().map(InvoiceLineResponse::from).toList()
                        : List.of())
                .subtotalAmount(invoice.getSubtotalAmount())
                .discountAmount(invoice.getDiscountAmount())
                .taxAmount(invoice.getTaxAmount())
                .paidAmount(paid)
                .remainingAmount(remaining)
                .pdfGeneratedAt(invoice.getPdfGeneratedAt())
                .pdfLanguage(invoice.getPdfLanguage())
                .pdfOutdated(invoice.isPdfOutdated())
                .emailedAt(invoice.getEmailedAt())
                .emailedTo(invoice.getEmailedTo())
                .build();
    }
}
