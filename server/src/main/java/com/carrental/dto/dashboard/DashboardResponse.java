package com.carrental.dto.dashboard;

import com.carrental.dto.payment.PaymentResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dashboard KPIs projection with real payment-derived metrics and fleet overview.
 */
@Data
@Builder
public class DashboardResponse {
    private long totalVehicles;
    private long rentedVehicles;
    private long maintenanceVehicles;
    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    private long totalReservations;
    private long totalClients;
    private long reservationsToday;
    private long reservationsThisMonth;
    private long upcomingReservations;
    private long activeContracts;
    private long pendingContracts;
    private long signedContracts;
    private long availableVehicles;
    private long reservedVehicles;

    // Payment KPIs
    private BigDecimal pendingPaymentsAmount;
    private long pendingPaymentsCount;
    private BigDecimal paymentsToday;
    private long paidInvoices;
    private long overdueInvoices;
    private BigDecimal refundAmount;
    private long refundCount;
    private List<PaymentResponse> recentTransactions;

    // Fleet detail lists — vehicle cards for the dashboard
    private List<Map<String, Object>> vehicles;
    private List<Map<String, Object>> activeRentals;
    private List<Map<String, Object>> upcomingReturns;

    // Activity
    private List<Object> recentActivity;

    // Alerts
    private List<Map<String, Object>> alerts;

    // Deposit KPIs
    private BigDecimal totalDepositsHeld;
    private long pendingReturns;
    private BigDecimal returnedDeposits;
    private BigDecimal depositDeductions;
    private BigDecimal depositRevenue;
}
