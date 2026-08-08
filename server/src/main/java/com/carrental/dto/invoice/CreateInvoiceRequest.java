package com.carrental.dto.invoice;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/invoices} — create an invoice.
 *
 * <p>Two modes, distinguished by {@link #contractId}:
 * <ul>
 *   <li><b>Contract-linked</b> ({@code contractId} set): every other field is
 *       ignored — invoiceNumber, client, rental period, and amount are all
 *       derived from the contract itself (see InvoiceService#syncInvoiceForContract).
 *       This is also idempotent: calling it again for the same contract updates
 *       the existing invoice instead of creating a duplicate.</li>
 *   <li><b>Manual</b> ({@code contractId} null): the exceptional/ad-hoc case —
 *       invoiceNumber, client, dates, and amount must be supplied explicitly.</li>
 * </ul>
 *
 * <p>Deliberately has no {@code status} field — status is never client-supplied
 * (see PaymentService#updateInvoiceStatus, the single place that computes it).
 */
@Data
public class CreateInvoiceRequest {

    /** When set, this is a contract-linked invoice — see class javadoc. */
    private Long contractId;

    private String invoiceNumber;

    private String clientName;

    private Long clientId;

    private LocalDate issueDate;

    private LocalDate dueDate;

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;
}
