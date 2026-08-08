package com.carrental.dto.invoice;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Monthly accounting rollup for the invoicing page — every figure is computed
 * from real Invoice/Payment/ContractExtension rows (see
 * InvoiceService#getMonthlyAccountingSummary), never estimated.
 */
@Data
@Builder
public class MonthlyAccountingSummary {
    private String month;
    private BigDecimal totalInvoiced;
    private BigDecimal totalCollected;
    private BigDecimal totalOutstanding;
    private BigDecimal totalOverdue;
    private BigDecimal totalPartiallyPaid;
    private BigDecimal totalCancelled;
    private BigDecimal totalRefunded;
    private long invoiceCount;
    private long contractCount;
    private BigDecimal rentalRevenue;
    private BigDecimal extensionRevenue;
}
