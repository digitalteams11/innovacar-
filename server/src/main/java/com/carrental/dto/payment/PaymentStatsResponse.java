package com.carrental.dto.payment;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated payment statistics for dashboard and reporting.
 */
@Data
@Builder
public class PaymentStatsResponse {

    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    private BigDecimal pendingAmount;
    private long pendingCount;
    private long paidInvoices;
    private long overdueInvoices;
    private BigDecimal refundAmount;
    private long refundCount;
    private List<PaymentResponse> recentTransactions;
}
