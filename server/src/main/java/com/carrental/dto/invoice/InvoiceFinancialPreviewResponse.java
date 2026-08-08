package com.carrental.dto.invoice;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Server-computed financial summary shown by the "New Invoice" modal's
 * contract-linked mode BEFORE the invoice is actually created — so the user
 * sees the real contract total, what's already been paid/invoiced, and the
 * outstanding balance, never a value the frontend guessed itself
 * (see InvoiceService#getFinancialPreviewForContract).
 */
@Data
@Builder
public class InvoiceFinancialPreviewResponse {
    private Long contractId;
    private String contractNumber;
    private Long clientId;
    private String clientName;
    private String vehicleLabel;
    private LocalDate rentalStart;
    private LocalDate rentalEnd;
    private Integer rentalDays;

    private BigDecimal contractTotal;
    private BigDecimal alreadyPaid;
    /** Amount already invoiced on the contract's current active invoice, 0 if none exists yet. */
    private BigDecimal previouslyInvoiced;
    private BigDecimal outstandingBalance;
    /** What creating/syncing the invoice now would change the invoice amount to (= contractTotal). */
    private BigDecimal newInvoiceTotal;

    private boolean contractCancelled;
    private boolean fullyPaid;
    private boolean hasActiveInvoice;
    private Long activeInvoiceId;
    private String activeInvoiceNumber;
    private String activeInvoiceStatus;
}
