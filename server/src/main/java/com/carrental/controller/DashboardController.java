package com.carrental.controller;

import com.carrental.dto.dashboard.DashboardResponse;
import com.carrental.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dashboard REST controller.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping({"", "/", "/summary"})
    public ResponseEntity<Map<String, Object>> getDashboardMetrics() {
        DashboardResponse metrics = dashboardService.getDashboardMetrics();
        Map<String, Object> data = toMap(metrics);
        Map<String, Object> response = new LinkedHashMap<>(data);
        response.put("success", true);
        response.put("message", "Dashboard loaded successfully");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts() {
        DashboardResponse metrics = dashboardService.getDashboardMetrics();
        List<?> alerts = metrics.getAlerts() != null ? metrics.getAlerts() : List.of();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Alerts loaded");
        response.put("data", alerts);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toMap(DashboardResponse metrics) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalVehicles", metrics.getTotalVehicles());
        data.put("fleet", metrics.getTotalVehicles());
        data.put("availableVehicles", metrics.getAvailableVehicles());
        data.put("rentedVehicles", metrics.getRentedVehicles());
        data.put("reservedVehicles", metrics.getReservedVehicles());
        data.put("activeRentals", metrics.getActiveContracts());
        data.put("activeContracts", metrics.getActiveContracts());
        data.put("totalClients", metrics.getTotalClients());
        data.put("totalReservations", metrics.getTotalReservations());
        data.put("reservations", metrics.getTotalReservations());
        data.put("reservationsToday", metrics.getReservationsToday());
        data.put("reservationsThisMonth", metrics.getReservationsThisMonth());
        data.put("upcomingReservations", metrics.getUpcomingReservations());
        data.put("pendingContracts", metrics.getPendingContracts());
        data.put("signedContracts", metrics.getSignedContracts());
        data.put("monthlyRevenue", metrics.getMonthlyRevenue());
        data.put("totalRevenue", metrics.getTotalRevenue());
        data.put("paymentsToday", metrics.getPaymentsToday());
        data.put("pendingPaymentsAmount", metrics.getPendingPaymentsAmount());
        data.put("pendingPaymentsCount", metrics.getPendingPaymentsCount());
        data.put("paidInvoices", metrics.getPaidInvoices());
        data.put("overdueInvoices", metrics.getOverdueInvoices());
        data.put("refundAmount", metrics.getRefundAmount());
        data.put("refundCount", metrics.getRefundCount());
        data.put("recentTransactions", metrics.getRecentTransactions());
        data.put("vehicles", metrics.getVehicles());
        data.put("activeRentals", metrics.getActiveRentals());
        data.put("upcomingReturns", metrics.getUpcomingReturns());
        data.put("recentActivity", metrics.getRecentActivity());
        data.put("alerts", metrics.getAlerts());
        data.put("maintenanceVehicles", metrics.getMaintenanceVehicles());
        data.put("totalDepositsHeld", metrics.getTotalDepositsHeld());
        data.put("depositsHeld", metrics.getTotalDepositsHeld());
        data.put("pendingReturns", metrics.getPendingReturns());
        data.put("returnedDeposits", metrics.getReturnedDeposits());
        data.put("depositDeductions", metrics.getDepositDeductions());
        data.put("deductions", metrics.getDepositDeductions());
        data.put("depositRevenue", metrics.getDepositRevenue());
        return data;
    }
}

