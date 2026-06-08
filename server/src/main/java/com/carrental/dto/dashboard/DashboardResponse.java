package com.carrental.dto.dashboard;

import com.carrental.dto.payment.PaymentResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard KPIs projection with real payment-derived metrics.
 */
@Data
@Builder
public class DashboardResponse {
    private long totalVehicles;
    private long rentedVehicles;
    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    private long totalReservations;
    private long totalClients;

    // Payment KPIs
    private BigDecimal pendingPaymentsAmount;
    private long pendingPaymentsCount;
    private long paidInvoices;
    private long overdueInvoices;
    private BigDecimal refundAmount;
    private long refundCount;
    private List<PaymentResponse> recentTransactions;

    // Deposit KPIs
    private BigDecimal totalDepositsHeld;
    private long pendingReturns;
    private BigDecimal returnedDeposits;
    private BigDecimal depositDeductions;
    private BigDecimal depositRevenue;
}
