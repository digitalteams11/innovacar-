package com.carrental.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Global dashboard statistics for the Innovax Technologies super-admin portal.
 * All financial metrics are computed from real payment data.
 */
@Data
@Builder
public class GlobalDashboardStats {
    private long totalAgencies;
    private long activeAgencies;
    private long trialAgencies;
    private long expiredAgencies;
    private long suspendedAgencies;

    private long totalUsers;
    private long activeUsers;
    private long totalVehicles;
    private long totalReservations;
    private long totalContracts;

    // Rental revenue (from tenant rental payments)
    private BigDecimal monthlyRevenue;
    private BigDecimal yearlyRevenue;
    private BigDecimal totalRevenue;

    // SaaS subscription metrics
    private BigDecimal mrr;
    private BigDecimal arr;
    private BigDecimal subscriptionRevenue;
    private long activeSubscribers;
    private long expiredSubscribers;
    private long trialUsers;
    private long failedPaymentsLast30Days;

    private long activeGpsConnections;
    private long totalGpsDevices;

    private long openTickets;
    private long unresolvedAlerts;
}
