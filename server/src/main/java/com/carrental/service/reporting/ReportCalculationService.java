package com.carrental.service.reporting;

import com.carrental.dto.reporting.ReportDataset;
import com.carrental.dto.reporting.ReportDataset.*;
import com.carrental.entity.*;
import com.carrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic financial/operational calculation engine — spec sections 4-7.
 * Pure aggregation: no PDF, no email, no AI. Every figure comes from real
 * repository queries; nothing here is invented. All money math uses
 * {@link BigDecimal}, never floating point.
 */
@Service
@RequiredArgsConstructor
public class ReportCalculationService {

    public static final String CALCULATION_VERSION = "v1";

    private final PaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleMaintenanceRepository maintenanceRepository;
    private final ClientRepository clientRepository;
    private final ReportPeriodResolver periodResolver;

    private static final Set<ContractStatus> ACTIVE_ISH_STATUSES = EnumSet.of(
            ContractStatus.ACTIVE, ContractStatus.SIGNED, ContractStatus.PARTIALLY_SIGNED,
            ContractStatus.PAID, ContractStatus.COMPLETED);

    private static final Set<PaymentType> REVENUE_TYPES = EnumSet.of(
            PaymentType.RENTAL, PaymentType.DAMAGE_FEE, PaymentType.EXTRA_CHARGE, PaymentType.OTHER);

    @Transactional(readOnly = true)
    public ReportDataset calculate(Tenant tenant, ReportType reportType, ReportPeriodResolver.Period period, ZoneId zone) {
        Long tenantId = tenant.getId();
        ReportPeriodResolver.Period previous = periodResolver.previousPeriod(reportType, period, zone);

        PeriodFigures current = computePeriodFigures(tenantId, period, zone);
        PeriodFigures priorPeriod = computePeriodFigures(tenantId, previous, zone);

        PeriodComparison comparison = new PeriodComparison(
                MetricChange.of(priorPeriod.financial.grossRevenue(), current.financial.grossRevenue()),
                MetricChange.of(priorPeriod.financial.expenses(), current.financial.expenses()),
                MetricChange.of(priorPeriod.financial.profit(), current.financial.profit()),
                MetricChange.of(BigDecimal.valueOf(priorPeriod.operations.totalReservations()),
                        BigDecimal.valueOf(current.operations.totalReservations())),
                MetricChange.of(priorPeriod.operations.occupancyRate(), current.operations.occupancyRate()),
                MetricChange.of(priorPeriod.maintenance.totalCost(), current.maintenance.totalCost()),
                MetricChange.of(priorPeriod.financial.outstandingBalance(), current.financial.outstandingBalance())
        );

        return new ReportDataset(
                tenantId, reportType.name(), period.start(), period.end(), CALCULATION_VERSION,
                current.financial, current.operations, current.fleet,
                current.topVehicles, current.lowVehicles, current.clients, current.maintenance,
                comparison);
    }

    /**
     * Exposed (not just an internal implementation detail of {@link #calculate})
     * so callers that need the same real, exclusion-correct figures for an
     * arbitrary live window — e.g. the dashboard's "today" / "this month so far"
     * cards — can reuse this exact math instead of re-deriving it. Never invent
     * a second revenue/expense calculation; always go through this.
     */
    public record PeriodFigures(
            FinancialSummary financial, OperationsSummary operations, FleetSummary fleet,
            List<VehiclePerformance> topVehicles, List<VehiclePerformance> lowVehicles,
            ClientsSummary clients, MaintenanceSummary maintenance,
            List<VehiclePerformance> allVehiclePerformance) {}

    /** Public entry point for an arbitrary (not necessarily "closed") period — see {@link PeriodFigures}. */
    @Transactional(readOnly = true)
    public PeriodFigures computeFigures(Long tenantId, LocalDateTime start, LocalDateTime end, ZoneId zone) {
        return computePeriodFigures(tenantId, new ReportPeriodResolver.Period(start, end), zone);
    }

    private PeriodFigures computePeriodFigures(Long tenantId, ReportPeriodResolver.Period period, ZoneId zone) {
        LocalDate periodStartDate = period.start().atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate();
        LocalDate periodEndDate = period.end().atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate();
        long periodDays = Math.max(1, ChronoUnit.DAYS.between(periodStartDate, periodEndDate));

        List<Payment> payments = paymentRepository.findAllForReportingPeriod(tenantId, period.start(), period.end());
        List<Contract> contracts = contractRepository.findAllOverlappingPeriod(tenantId, periodStartDate, periodEndDate);
        List<Reservation> reservations = reservationRepository.findAllStartingInPeriod(tenantId, periodStartDate, periodEndDate);
        List<Vehicle> vehicles = vehicleRepository.findAllByTenantId(tenantId);
        List<VehicleMaintenance> maintenanceCompleted = maintenanceRepository
                .findAllByTenantIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        tenantId, MaintenanceStatus.COMPLETED, period.start(), period.end());
        List<VehicleMaintenance> maintenanceCreated = maintenanceRepository
                .findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(tenantId, period.start(), period.end());

        BigDecimal grossRevenue = sumPayments(payments, p -> REVENUE_TYPES.contains(p.getType())
                && (p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.PARTIALLY_PAID));
        BigDecimal refunds = sumPayments(payments, p -> p.getStatus() == PaymentStatus.REFUNDED
                || p.getType() == PaymentType.REFUND);
        BigDecimal collected = grossRevenue;
        BigDecimal maintenanceCost = sumMaintenance(maintenanceCompleted);
        BigDecimal expenses = maintenanceCost.add(refunds);
        BigDecimal netRevenue = grossRevenue.subtract(refunds);
        BigDecimal profit = netRevenue.subtract(expenses);
        BigDecimal loss = profit.signum() < 0 ? profit.abs() : BigDecimal.ZERO;

        List<Contract> outstandingContracts = contracts.stream()
                .filter(c -> c.getStatus() != ContractStatus.CANCELLED && c.getStatus() != ContractStatus.DRAFT)
                .filter(c -> c.getRemainingAmount() != null && c.getRemainingAmount().signum() > 0)
                .toList();
        BigDecimal outstandingBalance = outstandingContracts.stream()
                .map(Contract::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overduePayments = outstandingContracts.stream()
                .filter(c -> c.getEndDate() != null && c.getEndDate().isBefore(LocalDate.now()))
                .map(Contract::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        long completedRentalsCount = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.COMPLETED || c.getStatus() == ContractStatus.PAID)
                .count();
        BigDecimal avgRevenuePerRental = completedRentalsCount == 0 ? BigDecimal.ZERO
                : grossRevenue.divide(BigDecimal.valueOf(completedRentalsCount), 2, RoundingMode.HALF_UP);

        FinancialSummary financial = new FinancialSummary(
                round2(grossRevenue), round2(netRevenue), round2(expenses), round2(profit), round2(loss),
                round2(outstandingBalance), round2(collected), round2(overduePayments), round2(refunds),
                round2(avgRevenuePerRental));

        long totalReservations = reservations.size();
        long confirmedReservations = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.CONVERTED_TO_CONTRACT)
                .count();
        long cancelledReservations = reservations.stream().filter(r -> r.getStatus() == ReservationStatus.CANCELLED).count();
        long activeContracts = contracts.stream().filter(c -> c.getStatus() == ContractStatus.ACTIVE).count();
        long completedContracts = contracts.stream().filter(c -> c.getStatus() == ContractStatus.COMPLETED).count();

        List<Contract> nonCancelledContracts = contracts.stream()
                .filter(c -> c.getStatus() != ContractStatus.CANCELLED && c.getStartDate() != null && c.getEndDate() != null)
                .toList();
        BigDecimal avgDuration = nonCancelledContracts.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(nonCancelledContracts.stream()
                                .mapToLong(c -> ChronoUnit.DAYS.between(c.getStartDate(), c.getEndDate()) + 1)
                                .average().orElse(0))
                        .setScale(1, RoundingMode.HALF_UP);

        int totalVehicles = vehicles.size();
        BigDecimal rentedVehicleDays = nonCancelledContracts.stream()
                .map(c -> BigDecimal.valueOf(overlapDays(c.getStartDate(), c.getEndDate(), periodStartDate, periodEndDate)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal occupancyRate = totalVehicles == 0 ? BigDecimal.ZERO
                : rentedVehicleDays.divide(BigDecimal.valueOf((long) totalVehicles * periodDays), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);

        OperationsSummary operations = new OperationsSummary(
                (int) totalReservations, (int) confirmedReservations, (int) cancelledReservations,
                (int) completedRentalsCount, (int) activeContracts, (int) completedContracts,
                avgDuration, occupancyRate, 0, 0);

        int availableVehicles = countByStatus(vehicles, VehicleStatus.AVAILABLE);
        int rentedVehicles = countByStatus(vehicles, VehicleStatus.RENTED);
        int reservedVehicles = countByStatus(vehicles, VehicleStatus.RESERVED);
        int maintenanceVehicles = countByStatus(vehicles, VehicleStatus.IN_MAINTENANCE) + countByStatus(vehicles, VehicleStatus.MAINTENANCE);
        int inactiveVehicles = countByStatus(vehicles, VehicleStatus.OUT_OF_SERVICE)
                + countByStatus(vehicles, VehicleStatus.ARCHIVED) + countByStatus(vehicles, VehicleStatus.SOLD);
        BigDecimal fleetUtilizationRate = totalVehicles == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(rentedVehicles + reservedVehicles)
                        .divide(BigDecimal.valueOf(totalVehicles), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal revenuePerVehicle = totalVehicles == 0 ? BigDecimal.ZERO
                : grossRevenue.divide(BigDecimal.valueOf(totalVehicles), 2, RoundingMode.HALF_UP);
        BigDecimal maintenanceCostPerVehicle = totalVehicles == 0 ? BigDecimal.ZERO
                : maintenanceCost.divide(BigDecimal.valueOf(totalVehicles), 2, RoundingMode.HALF_UP);

        FleetSummary fleet = new FleetSummary(totalVehicles, availableVehicles, rentedVehicles, reservedVehicles,
                maintenanceVehicles, inactiveVehicles, fleetUtilizationRate, revenuePerVehicle, maintenanceCostPerVehicle);

        List<VehiclePerformance> vehiclePerformances = computeVehiclePerformance(
                nonCancelledContracts, maintenanceCompleted, periodStartDate, periodEndDate, periodDays);
        List<VehiclePerformance> topVehicles = vehiclePerformances.stream()
                .sorted(Comparator.comparing(VehiclePerformance::profitContribution).reversed())
                .limit(5).toList();
        List<VehiclePerformance> lowVehicles = vehiclePerformances.stream()
                .sorted(Comparator.comparing(VehiclePerformance::profitContribution))
                .limit(5).toList();

        ClientsSummary clients = computeClientsSummary(tenantId, nonCancelledContracts, outstandingContracts, periodStartDate);

        MaintenanceSummary maintenance = computeMaintenanceSummary(maintenanceCreated, maintenanceCompleted, maintenanceCost);

        return new PeriodFigures(financial, operations, fleet, topVehicles, lowVehicles, clients, maintenance, vehiclePerformances);
    }

    private List<VehiclePerformance> computeVehiclePerformance(List<Contract> contracts, List<VehicleMaintenance> maintenance,
                                                                 LocalDate periodStartDate, LocalDate periodEndDate, long periodDays) {
        Map<Long, BigDecimal> revenueByVehicle = new HashMap<>();
        Map<Long, Long> daysByVehicle = new HashMap<>();
        Map<Long, String> labelByVehicle = new HashMap<>();
        for (Contract c : contracts) {
            if (c.getVehicle() == null) continue;
            Long vehicleId = c.getVehicle().getId();
            revenueByVehicle.merge(vehicleId, safe(c.getPaidAmount()), BigDecimal::add);
            daysByVehicle.merge(vehicleId, overlapDays(c.getStartDate(), c.getEndDate(), periodStartDate, periodEndDate), Long::sum);
            labelByVehicle.putIfAbsent(vehicleId, vehicleLabel(c));
        }
        Map<Long, BigDecimal> expensesByVehicle = new HashMap<>();
        for (VehicleMaintenance m : maintenance) {
            if (m.getVehicle() == null) continue;
            expensesByVehicle.merge(m.getVehicle().getId(), safe(m.getCost()), BigDecimal::add);
        }
        Set<Long> vehicleIds = new HashSet<>(revenueByVehicle.keySet());
        vehicleIds.addAll(expensesByVehicle.keySet());

        List<VehiclePerformance> result = new ArrayList<>();
        for (Long vehicleId : vehicleIds) {
            BigDecimal revenue = revenueByVehicle.getOrDefault(vehicleId, BigDecimal.ZERO);
            BigDecimal veExpenses = expensesByVehicle.getOrDefault(vehicleId, BigDecimal.ZERO);
            BigDecimal profitContribution = revenue.subtract(veExpenses);
            long rentedDays = daysByVehicle.getOrDefault(vehicleId, 0L);
            BigDecimal utilization = BigDecimal.valueOf(rentedDays)
                    .divide(BigDecimal.valueOf(periodDays), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            result.add(new VehiclePerformance(vehicleId, labelByVehicle.getOrDefault(vehicleId, "Vehicle #" + vehicleId),
                    round2(revenue), round2(veExpenses), round2(profitContribution), utilization));
        }
        return result;
    }

    private ClientsSummary computeClientsSummary(Long tenantId, List<Contract> contracts, List<Contract> outstandingContracts,
                                                  LocalDate periodStartDate) {
        Map<Long, LocalDate> firstContractByClient = contractRepository.findFirstContractStartDateByClient(tenantId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (LocalDate) row[1]));

        Map<Long, BigDecimal> revenueByClient = new HashMap<>();
        Map<Long, String> nameByClient = new HashMap<>();
        Set<Long> clientsInPeriod = new HashSet<>();
        for (Contract c : contracts) {
            if (c.getClient() == null) continue;
            Long clientId = c.getClient().getId();
            clientsInPeriod.add(clientId);
            revenueByClient.merge(clientId, safe(c.getPaidAmount()), BigDecimal::add);
            nameByClient.putIfAbsent(clientId, c.getClientFullName() != null ? c.getClientFullName() : c.getClientName());
        }

        int newClients = 0;
        int returningClients = 0;
        for (Long clientId : clientsInPeriod) {
            LocalDate firstContract = firstContractByClient.get(clientId);
            if (firstContract != null && !firstContract.isBefore(periodStartDate)) {
                newClients++;
            } else {
                returningClients++;
            }
        }

        List<ClientRevenue> topClients = revenueByClient.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> new ClientRevenue(e.getKey(),
                        nameByClient.getOrDefault(e.getKey(), clientRepository.findByIdAndTenantId(e.getKey(), tenantId)
                                .map(Client::getName).orElse("Client #" + e.getKey())),
                        round2(e.getValue())))
                .toList();

        long clientsWithOverdue = outstandingContracts.stream()
                .filter(c -> c.getClient() != null)
                .map(c -> c.getClient().getId())
                .distinct().count();

        return new ClientsSummary(newClients, returningClients, topClients, (int) clientsWithOverdue);
    }

    private MaintenanceSummary computeMaintenanceSummary(List<VehicleMaintenance> created, List<VehicleMaintenance> completed,
                                                           BigDecimal totalCost) {
        int totalOrders = created.size();
        int completedOrders = (int) created.stream().filter(m -> m.getStatus() == MaintenanceStatus.COMPLETED).count();
        int activeOrders = (int) created.stream()
                .filter(m -> m.getStatus() == MaintenanceStatus.SCHEDULED || m.getStatus() == MaintenanceStatus.IN_PROGRESS)
                .count();

        VehicleMaintenanceHighlight highest = completed.stream()
                .max(Comparator.comparing(m -> safe(m.getCost())))
                .map(m -> new VehicleMaintenanceHighlight(
                        m.getVehicle() != null ? m.getVehicle().getId() : null,
                        m.getVehicle() != null ? vehicleLabelFromVehicle(m.getVehicle()) : m.getTitle(),
                        round2(m.getCost())))
                .orElse(null);

        Map<Long, Long> countByVehicle = completed.stream()
                .filter(m -> m.getVehicle() != null)
                .collect(Collectors.groupingBy(m -> m.getVehicle().getId(), Collectors.counting()));
        int repeated = (int) countByVehicle.values().stream().filter(c -> c > 1).count();

        int upcoming = (int) created.stream()
                .filter(m -> m.getStatus() == MaintenanceStatus.SCHEDULED
                        && m.getScheduledAt() != null && m.getScheduledAt().isAfter(LocalDateTime.now()))
                .count();

        return new MaintenanceSummary(totalOrders, completedOrders, activeOrders, round2(totalCost), highest, repeated, upcoming);
    }

    private String vehicleLabel(Contract c) {
        String brand = c.getVehicleBrand();
        String model = c.getVehicleModel();
        if (brand == null && model == null) return "Vehicle #" + (c.getVehicle() != null ? c.getVehicle().getId() : "?");
        return ((brand != null ? brand : "") + " " + (model != null ? model : "")).trim();
    }

    private String vehicleLabelFromVehicle(Vehicle v) {
        return v.getMarque() != null ? v.getMarque() : "Vehicle #" + v.getId();
    }

    private long overlapDays(LocalDate contractStart, LocalDate contractEnd, LocalDate periodStart, LocalDate periodEndExclusive) {
        if (contractStart == null || contractEnd == null) return 0;
        LocalDate effectiveStart = contractStart.isAfter(periodStart) ? contractStart : periodStart;
        LocalDate periodLastInclusive = periodEndExclusive.minusDays(1);
        LocalDate effectiveEnd = contractEnd.isBefore(periodLastInclusive) ? contractEnd : periodLastInclusive;
        long days = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
        return Math.max(0, days);
    }

    private int countByStatus(List<Vehicle> vehicles, VehicleStatus status) {
        return (int) vehicles.stream().filter(v -> v.getStatut() == status).count();
    }

    private BigDecimal sumPayments(List<Payment> payments, java.util.function.Predicate<Payment> filter) {
        return payments.stream().filter(filter).map(Payment::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumMaintenance(List<VehicleMaintenance> list) {
        return list.stream().map(VehicleMaintenance::getCost).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal round2(BigDecimal value) {
        return safe(value).setScale(2, RoundingMode.HALF_UP);
    }
}
