package com.carrental.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full deterministic calculation result for one report period. Every number
 * here comes from real database aggregation in {@code ReportCalculationService}
 * — nothing is invented or AI-generated. Serialized as JSON into
 * {@code reports.calculation_snapshot}, the source of truth for re-download.
 */
public record ReportDataset(
        Long tenantId,
        String reportType,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String calculationVersion,
        FinancialSummary financial,
        OperationsSummary operations,
        FleetSummary fleet,
        List<VehiclePerformance> topVehicles,
        List<VehiclePerformance> lowVehicles,
        ClientsSummary clients,
        MaintenanceSummary maintenance,
        PeriodComparison comparison
) {

    public record FinancialSummary(
            BigDecimal grossRevenue,
            BigDecimal netRevenue,
            BigDecimal expenses,
            BigDecimal profit,
            BigDecimal loss,
            BigDecimal outstandingBalance,
            BigDecimal collectedPayments,
            BigDecimal overduePayments,
            BigDecimal refunds,
            BigDecimal avgRevenuePerRental
    ) {}

    public record OperationsSummary(
            int totalReservations,
            int confirmedReservations,
            int cancelledReservations,
            int completedRentals,
            int activeContracts,
            int completedContracts,
            BigDecimal avgRentalDurationDays,
            BigDecimal occupancyRate,
            int returnInspectionsCompleted,
            int lateReturns
    ) {}

    public record FleetSummary(
            int totalVehicles,
            int availableVehicles,
            int rentedVehicles,
            int reservedVehicles,
            int maintenanceVehicles,
            int inactiveVehicles,
            BigDecimal fleetUtilizationRate,
            BigDecimal revenuePerVehicle,
            BigDecimal maintenanceCostPerVehicle
    ) {}

    public record VehiclePerformance(
            Long vehicleId,
            String label,
            BigDecimal revenue,
            BigDecimal expenses,
            BigDecimal profitContribution,
            BigDecimal utilizationRate
    ) {}

    public record ClientRevenue(Long clientId, String name, BigDecimal revenue) {}

    public record ClientsSummary(
            int newClients,
            int returningClients,
            List<ClientRevenue> topClients,
            int clientsWithOverdueBalance
    ) {}

    public record VehicleMaintenanceHighlight(Long vehicleId, String label, BigDecimal cost) {}

    public record MaintenanceSummary(
            int totalOrders,
            int completedOrders,
            int activeOrders,
            BigDecimal totalCost,
            VehicleMaintenanceHighlight highestCostOrder,
            int vehiclesWithRepeatedMaintenance,
            int upcomingScheduled
    ) {}

    /** previousValue/currentValue absolute; percentChange is null (not zero) when previousValue is zero — never NaN/Infinity. */
    public record MetricChange(
            BigDecimal previousValue,
            BigDecimal currentValue,
            BigDecimal absoluteChange,
            BigDecimal percentChange,
            boolean percentAvailable
    ) {
        public static MetricChange of(BigDecimal previous, BigDecimal current) {
            BigDecimal prev = previous != null ? previous : BigDecimal.ZERO;
            BigDecimal curr = current != null ? current : BigDecimal.ZERO;
            BigDecimal absoluteChange = curr.subtract(prev);
            if (prev.signum() == 0) {
                return new MetricChange(prev, curr, absoluteChange, null, false);
            }
            BigDecimal percent = absoluteChange
                    .divide(prev.abs(), 6, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            return new MetricChange(prev, curr, absoluteChange, percent, true);
        }
    }

    public record PeriodComparison(
            MetricChange revenue,
            MetricChange expenses,
            MetricChange profit,
            MetricChange reservations,
            MetricChange utilization,
            MetricChange maintenanceCost,
            MetricChange outstandingBalance
    ) {}
}
