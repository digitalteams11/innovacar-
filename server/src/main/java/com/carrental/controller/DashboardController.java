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
    private final com.carrental.service.DashboardIntelligenceService dashboardIntelligenceService;
    private final com.carrental.repository.DashboardLayoutRepository dashboardLayoutRepository;

    /**
     * Server-persisted "Customize Dashboard" layout (spec section 16) — keyed on
     * the authenticated user's own id, never a client-supplied id, so a user can
     * never read/overwrite another user's layout. localStorage remains an
     * instant-paint cache on the frontend; this is the source of truth.
     */
    @GetMapping("/layout")
    public ResponseEntity<Map<String, Object>> getLayout(@org.springframework.security.core.annotation.AuthenticationPrincipal com.carrental.entity.User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        dashboardLayoutRepository.findByUserId(user.getId()).ifPresentOrElse(
                layout -> {
                    data.put("widgetsJson", layout.getWidgetsJson());
                    data.put("updatedAt", layout.getUpdatedAt());
                },
                () -> data.put("widgetsJson", null));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.PutMapping("/layout")
    public ResponseEntity<Map<String, Object>> saveLayout(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.carrental.entity.User user,
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        Object widgets = body.get("widgets");
        String widgetsJson = widgets == null ? "[]" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(widgets).toString();
        com.carrental.entity.DashboardLayout layout = dashboardLayoutRepository.findByUserId(user.getId())
                .orElseGet(() -> com.carrental.entity.DashboardLayout.builder().userId(user.getId()).widgetsJson("[]").build());
        layout.setWidgetsJson(widgetsJson);
        layout.setUpdatedAt(java.time.LocalDateTime.now());
        dashboardLayoutRepository.save(layout);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Dashboard layout saved");
        return ResponseEntity.ok(response);
    }

    /**
     * Real operational intelligence: today's operations, the prioritized action
     * queue, the financial control center, vehicle profitability, payment risk,
     * and maintenance intelligence — see spec sections 1-4/10/11. One call
     * (not six) to keep the dashboard's initial paint fast; each section is
     * independently null-safe if its own computation fails.
     */
    @GetMapping("/operations-center")
    public ResponseEntity<Map<String, Object>> getOperationsCenter() {
        Map<String, Object> data = dashboardIntelligenceService.operationsCenter();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Operations center loaded");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    /** Real, aggregated daily revenue-trend series for the Revenue Trend chart (spec section 7). */
    @GetMapping("/revenue-trend")
    public ResponseEntity<Map<String, Object>> getRevenueTrend(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "30d") String range) {
        List<Map<String, Object>> series = dashboardIntelligenceService.revenueTrend(range);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Revenue trend loaded");
        response.put("data", series);
        return ResponseEntity.ok(response);
    }

    /** Deterministic (no invented numbers) insight sentence — Complete-pack "AI Insights" widget's safe baseline. */
    @GetMapping("/insight")
    @org.springframework.security.access.prepost.PreAuthorize("@featureAccessService.isEnabledForCurrentTenant('AI_REPORTS')")
    public ResponseEntity<Map<String, Object>> getInsight() {
        Map<String, Object> data = dashboardIntelligenceService.insight();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Insight computed");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

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

