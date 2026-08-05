package com.carrental.service;

import com.carrental.dto.reporting.ReportDataset.VehiclePerformance;
import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.reporting.ReportCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/**
 * Backs the dashboard's "Today's Operations", "Action Required", "Financial
 * Control Center", "Vehicle Profitability", "Payment Risk", and "Maintenance
 * Intelligence" sections — every number here is a real repository-backed
 * aggregate (money via {@link BigDecimal}, reusing {@link ReportCalculationService}
 * for anything the reporting system already computes correctly, so the
 * dashboard and the monthly report can never silently disagree on the same
 * figure). Nothing here is decorative: every list item carries the exact
 * entity id needed to deep-link to the record, never a generic page.
 *
 * <p>Every top-level section is computed independently and wrapped so one
 * failing sub-computation never blanks the whole payload (spec: "one failing
 * widget must not break the dashboard").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardIntelligenceService {

    private final VehicleRepository vehicleRepository;
    private final ContractRepository contractRepository;
    private final ReservationRepository reservationRepository;
    private final VehicleMaintenanceRepository maintenanceRepository;
    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final ReportCalculationService reportCalculationService;
    private final PaymentRepository paymentRepository;
    private final FeatureAccessService featureAccessService;

    private static final Set<PaymentType> REVENUE_TYPES = EnumSet.of(
            PaymentType.RENTAL, PaymentType.DAMAGE_FEE, PaymentType.EXTRA_CHARGE, PaymentType.OTHER);

    private static final Set<ContractStatus> WAITING_SIGNATURE_STATUSES = EnumSet.of(
            ContractStatus.WAITING_SIGNATURE, ContractStatus.WAITING_CLIENT_SIGNATURE,
            ContractStatus.PENDING_SIGNATURE, ContractStatus.PARTIALLY_SIGNED);

    private static final Set<ContractStatus> LIVE_CONTRACT_STATUSES = EnumSet.of(
            ContractStatus.ACTIVE, ContractStatus.SIGNED, ContractStatus.PAID);

    @Transactional(readOnly = true)
    public Map<String, Object> operationsCenter() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) throw new IllegalArgumentException("Current user is not linked to an agency.");
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        List<Vehicle> vehicles = safe("vehicles", List.of(), () -> vehicleRepository.findAllByTenantId(tenantId));
        List<Contract> contracts = safe("contracts", List.of(), () -> contractRepository.findAllByTenantId(tenantId));
        List<Reservation> reservations = safe("reservations", List.of(), () -> reservationRepository.findAllByTenantId(tenantId));
        List<VehicleMaintenance> maintenance = safe("maintenance", List.of(), () -> maintenanceRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayOperations", safe("todayOperations", List.of(), () -> todayOperations(reservations, contracts, maintenance, vehicles, today)));
        result.put("actionQueue", safe("actionQueue", List.of(), () -> actionQueue(tenant, reservations, contracts, maintenance, vehicles, clientsSafe(tenantId), today)));
        result.put("financial", safe("financial", null, () -> financialControlCenter(tenantId, zone, today)));
        // Server-side enforced, not just hidden in the widget registry — a client that calls this
        // endpoint directly must never receive Vehicle Profitability data for a plan that doesn't
        // include ADVANCED_REPORTS, even though the frontend already omits the widget itself.
        boolean advancedReports = safe("advancedReportsFeatureCheck", false,
                () -> featureAccessService.isEnabledForCurrentTenant("ADVANCED_REPORTS"));
        result.put("vehicleProfitability", advancedReports
                ? safe("vehicleProfitability", null, () -> vehicleProfitability(tenantId, zone, today))
                : null);
        result.put("paymentRisk", safe("paymentRisk", null, () -> paymentRisk(contracts, today)));
        result.put("maintenanceIntelligence", safe("maintenanceIntelligence", null, () -> maintenanceIntelligence(maintenance, today)));
        result.put("fleetHealth", safe("fleetHealth", null, () -> fleetHealth(vehicles, maintenance, today)));
        result.put("contractPipeline", safe("contractPipeline", null, () -> contractPipeline(contracts, today)));
        result.put("reservationFunnel", safe("reservationFunnel", null, () -> reservationFunnel(reservations)));
        return result;
    }

    private List<Client> clientsSafe(Long tenantId) {
        return safe("clients", List.of(), () -> clientRepository.findAllByTenantId(tenantId));
    }

    // ── 1. Today's Operations ────────────────────────────────────────────────

    private List<Map<String, Object>> todayOperations(List<Reservation> reservations, List<Contract> contracts,
                                                        List<VehicleMaintenance> maintenance, List<Vehicle> vehicles, LocalDate today) {
        List<Map<String, Object>> items = new ArrayList<>();

        for (Reservation r : reservations) {
            if (r.getStatus() == ReservationStatus.CANCELLED) continue;
            if (today.equals(r.getDateStart())) {
                items.add(opItem("PICKUP_TODAY", clientName(r.getClient()), vehicleLabel(r.getVehicle()),
                        r.getStartTime() != null ? r.getStartTime().toString() : null, r.getStatus().name(),
                        "RESERVATION", r.getId(), r.getStatus() == ReservationStatus.PENDING ? "CONFIRM_RESERVATION" : "VIEW_RESERVATION"));
            }
            if (today.equals(r.getDateEnd())) {
                items.add(opItem("RETURN_TODAY", clientName(r.getClient()), vehicleLabel(r.getVehicle()),
                        r.getEndTime() != null ? r.getEndTime().toString() : null, r.getStatus().name(),
                        "RESERVATION", r.getId(), "VIEW_RESERVATION"));
            }
            if (r.getStatus() == ReservationStatus.PENDING) {
                items.add(opItem("RESERVATION_WAITING_CONFIRMATION", clientName(r.getClient()), vehicleLabel(r.getVehicle()),
                        r.getDateStart() != null ? r.getDateStart().toString() : null, r.getStatus().name(),
                        "RESERVATION", r.getId(), "CONFIRM_RESERVATION"));
            }
        }

        for (Contract c : contracts) {
            if (c.getStatus() == ContractStatus.CANCELLED) continue;
            if (LIVE_CONTRACT_STATUSES.contains(c.getStatus()) && today.equals(c.getEndDate())) {
                items.add(opItem("RETURN_TODAY", contractClientName(c), contractVehicleLabel(c), null, c.getStatus().name(),
                        "CONTRACT", c.getId(), "START_RETURN_INSPECTION"));
            }
            if (LIVE_CONTRACT_STATUSES.contains(c.getStatus()) && c.getEndDate() != null && c.getEndDate().isBefore(today)) {
                items.add(opItem("OVERDUE_RETURN", contractClientName(c), contractVehicleLabel(c), null, c.getStatus().name(),
                        "CONTRACT", c.getId(), "START_RETURN_INSPECTION"));
            }
            if (WAITING_SIGNATURE_STATUSES.contains(c.getStatus())) {
                items.add(opItem("CONTRACT_WAITING_SIGNATURE", contractClientName(c), contractVehicleLabel(c), null, c.getStatus().name(),
                        "CONTRACT", c.getId(), "OPEN_CONTRACT"));
            }
            if (c.getRemainingAmount() != null && c.getRemainingAmount().signum() > 0
                    && c.getEndDate() != null && c.getEndDate().equals(today)
                    && c.getStatus() != ContractStatus.CANCELLED) {
                items.add(opItem("PAYMENT_DUE_TODAY", contractClientName(c), contractVehicleLabel(c), null, c.getPaymentStatus(),
                        "CONTRACT", c.getId(), "RECORD_PAYMENT"));
            }
        }

        for (VehicleMaintenance m : maintenance) {
            if (m.getStatus() == MaintenanceStatus.CANCELLED) continue;
            LocalDate scheduled = m.getScheduledAt() != null ? m.getScheduledAt().toLocalDate() : null;
            if (today.equals(scheduled) && m.getStatus() == MaintenanceStatus.SCHEDULED) {
                items.add(opItem("MAINTENANCE_STARTING_TODAY", null, vehicleLabel(m.getVehicle()), null, m.getStatus().name(),
                        "MAINTENANCE", m.getVehicle() != null ? m.getVehicle().getId() : m.getId(), "OPEN_MAINTENANCE"));
            }
        }

        for (Vehicle v : vehicles) {
            if (v.getLastReturnedAt() != null && today.equals(v.getLastReturnedAt().toLocalDate())
                    && v.getStatut() == VehicleStatus.AVAILABLE) {
                items.add(opItem("VEHICLE_AVAILABLE_TODAY", null, vehicleLabel(v), null, v.getStatut().name(),
                        "VEHICLE", v.getId(), "VIEW_VEHICLE"));
            }
        }

        return items;
    }

    private Map<String, Object> opItem(String category, String clientName, String vehicleLabel, String time,
                                        String status, String entityType, Long entityId, String action) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("category", category);
        item.put("clientName", clientName);
        item.put("vehicle", vehicleLabel);
        item.put("time", time);
        item.put("status", status);
        item.put("entityType", entityType);
        item.put("entityId", entityId);
        item.put("action", action);
        return item;
    }

    // ── 2. Action Required Queue ─────────────────────────────────────────────

    private List<Map<String, Object>> actionQueue(Tenant tenant, List<Reservation> reservations, List<Contract> contracts,
                                                    List<VehicleMaintenance> maintenance, List<Vehicle> vehicles,
                                                    List<Client> clients, LocalDate today) {
        List<Map<String, Object>> queue = new ArrayList<>();
        LocalDate warnDate = today.plusDays(30);

        if (tenant != null && tenant.getStatus() == com.carrental.entity.SubscriptionStatus.TRIAL && tenant.isTrialExpired()) {
            queue.add(actionItem("CRITICAL", "SUBSCRIPTION_EXPIRED", "Your free trial has ended",
                    "No further access until a plan is chosen.", "SUBSCRIPTION", null, "OPEN_SUBSCRIPTION"));
        }

        for (Contract c : contracts) {
            if (c.getStatus() == ContractStatus.CANCELLED) continue;
            if (LIVE_CONTRACT_STATUSES.contains(c.getStatus()) && c.getEndDate() != null && c.getEndDate().isBefore(today)) {
                queue.add(actionItem("CRITICAL", "OVERDUE_RETURN",
                        "Vehicle return overdue for " + contractLabel(c),
                        "The rental period ended " + c.getEndDate() + " and the vehicle has not been returned.",
                        "CONTRACT", c.getId(), "START_RETURN_INSPECTION"));
            }
            if (c.getRemainingAmount() != null && c.getRemainingAmount().signum() > 0
                    && c.getEndDate() != null && c.getEndDate().isBefore(today)) {
                queue.add(actionItem("CRITICAL", "CONTRACT_UNPAID",
                        "Unpaid balance on " + contractLabel(c),
                        contractLabel(c) + " has an unpaid balance of " + c.getRemainingAmount() + " past its end date.",
                        "CONTRACT", c.getId(), "RECORD_PAYMENT"));
            }
            if (WAITING_SIGNATURE_STATUSES.contains(c.getStatus())) {
                queue.add(actionItem("HIGH", "CONTRACT_WAITING_SIGNATURE",
                        contractLabel(c) + " is waiting for a signature",
                        "The rental cannot start until this contract is fully signed.",
                        "CONTRACT", c.getId(), "OPEN_CONTRACT"));
            }
        }

        for (Reservation r : reservations) {
            if (r.getStatus() != ReservationStatus.PENDING && r.getStatus() != ReservationStatus.CONFIRMED) continue;
            if (r.getDateStart() != null && !r.getDateStart().isBefore(today) && !r.getDateStart().isAfter(today.plusDays(1))
                    && r.getContract() == null) {
                queue.add(actionItem("HIGH", "RESERVATION_STARTS_SOON_NO_CONTRACT",
                        "Reservation for " + clientName(r.getClient()) + " starts soon with no contract",
                        "Pickup is scheduled for " + r.getDateStart() + " and no contract has been created yet.",
                        "RESERVATION", r.getId(), "CREATE_CONTRACT"));
            }
        }

        for (Vehicle v : vehicles) {
            if (v.getInsuranceExpiration() != null && v.getInsuranceExpiration().isBefore(today)) {
                queue.add(actionItem("CRITICAL", "VEHICLE_DOCUMENT_EXPIRED",
                        vehicleLabel(v) + " insurance has expired",
                        "Insurance expired " + v.getInsuranceExpiration() + ".", "VEHICLE", v.getId(), "VIEW_VEHICLE"));
            }
            if (v.getTechnicalInspectionExpiration() != null && v.getTechnicalInspectionExpiration().isBefore(today)) {
                queue.add(actionItem("CRITICAL", "VEHICLE_DOCUMENT_EXPIRED",
                        vehicleLabel(v) + " technical inspection has expired",
                        "Technical inspection expired " + v.getTechnicalInspectionExpiration() + ".", "VEHICLE", v.getId(), "VIEW_VEHICLE"));
            }
        }

        for (VehicleMaintenance m : maintenance) {
            if (m.getStatus() != MaintenanceStatus.SCHEDULED) continue;
            LocalDate scheduled = m.getScheduledAt() != null ? m.getScheduledAt().toLocalDate() : null;
            if (scheduled != null && scheduled.isBefore(today)) {
                queue.add(actionItem("HIGH", "MAINTENANCE_OVERDUE",
                        "Maintenance overdue for " + vehicleLabel(m.getVehicle()),
                        "Scheduled for " + scheduled + " and still not started.", "MAINTENANCE",
                        m.getVehicle() != null ? m.getVehicle().getId() : m.getId(), "OPEN_MAINTENANCE"));
            } else if (scheduled != null && !scheduled.isAfter(today.plusDays(7))) {
                queue.add(actionItem("NORMAL", "MAINTENANCE_DUE_SOON",
                        "Maintenance due soon for " + vehicleLabel(m.getVehicle()),
                        "Scheduled for " + scheduled + ".", "MAINTENANCE",
                        m.getVehicle() != null ? m.getVehicle().getId() : m.getId(), "OPEN_MAINTENANCE"));
            }
        }

        for (Client c : clients) {
            boolean incomplete = isBlank(c.getEmail()) || isBlank(c.getPhone()) || isBlank(c.getDrivingLicense());
            if (incomplete) {
                queue.add(actionItem("NORMAL", "INCOMPLETE_CLIENT_PROFILE",
                        (c.getName() != null ? c.getName() : "A client") + " has an incomplete profile",
                        "Missing contact or driving-license details.", "CLIENT", c.getId(), "VIEW_CLIENT"));
            }
        }

        queue.sort(Comparator.comparingInt(item -> priorityRank((String) item.get("priority"))));
        return queue;
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            default -> 2;
        };
    }

    private Map<String, Object> actionItem(String priority, String category, String title, String reason,
                                            String entityType, Long entityId, String action) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("priority", priority);
        item.put("category", category);
        item.put("title", title);
        item.put("reason", reason);
        item.put("entityType", entityType);
        item.put("entityId", entityId);
        item.put("action", action);
        return item;
    }

    // ── 3. Financial Control Center ──────────────────────────────────────────

    /**
     * Real, aggregated (not decorative) daily revenue-trend series — spec
     * section 7. {@code range} is one of "7d", "30d", "year"; unrecognized
     * values fall back to 30 days. Buckets are computed directly from payments
     * (collected/refunds) and completed maintenance (expenses) grouped per
     * calendar day — a lighter-weight aggregation than {@link ReportCalculationService}'s
     * full per-vehicle/per-client breakdown, which isn't needed for a trend line.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> revenueTrend(String range) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) throw new IllegalArgumentException("Current user is not linked to an agency.");
        LocalDate today = LocalDate.now();
        LocalDate start = switch (range == null ? "30d" : range) {
            case "7d" -> today.minusDays(6);
            case "year" -> today.withDayOfYear(1);
            default -> today.minusDays(29);
        };

        List<Payment> payments = safe("trend payments", List.of(),
                () -> paymentRepository.findAllForReportingPeriod(tenantId, start.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        List<VehicleMaintenance> maintenanceCompleted = safe("trend maintenance", List.of(),
                () -> maintenanceRepository.findAllByTenantIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        tenantId, MaintenanceStatus.COMPLETED, start.atStartOfDay(), today.plusDays(1).atStartOfDay()));

        Map<LocalDate, BigDecimal> collectedByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> unpaidByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> expensesByDay = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            collectedByDay.put(d, BigDecimal.ZERO);
            unpaidByDay.put(d, BigDecimal.ZERO);
            expensesByDay.put(d, BigDecimal.ZERO);
        }
        for (Payment p : payments) {
            if (p.getPaymentDate() == null) continue;
            LocalDate day = p.getPaymentDate().toLocalDate();
            if (!collectedByDay.containsKey(day)) continue;
            BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            if (REVENUE_TYPES.contains(p.getType()) && (p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.PARTIALLY_PAID)) {
                collectedByDay.merge(day, amount, BigDecimal::add);
            } else if (p.getStatus() == PaymentStatus.PENDING) {
                unpaidByDay.merge(day, amount, BigDecimal::add);
            }
        }
        for (VehicleMaintenance m : maintenanceCompleted) {
            if (m.getCompletedAt() == null) continue;
            LocalDate day = m.getCompletedAt().toLocalDate();
            if (!expensesByDay.containsKey(day)) continue;
            expensesByDay.merge(day, m.getCost() != null ? m.getCost() : BigDecimal.ZERO, BigDecimal::add);
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate d : collectedByDay.keySet()) {
            BigDecimal collected = collectedByDay.get(d);
            BigDecimal unpaid = unpaidByDay.get(d);
            BigDecimal expenses = expensesByDay.get(d);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.toString());
            point.put("collected", collected);
            point.put("unpaid", unpaid);
            point.put("expenses", expenses);
            point.put("profit", collected.subtract(expenses));
            series.add(point);
        }
        return series;
    }

    // ── 13. Deterministic insight (AI may only phrase this, never invent numbers) ──

    /**
     * Picks the single most notable verified change this month vs last month
     * and phrases it as a plain sentence — the safe fallback (and, for now,
     * the only implementation) behind the "AI Insights" widget. No AI call:
     * every number quoted here is real (spec: "the AI must receive verified
     * summary data... do not let AI invent numbers" — until an AI layer is
     * wired on top of this, this deterministic sentence IS the insight).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> insight() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) throw new IllegalArgumentException("Current user is not linked to an agency.");
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);
        LocalDateTime startOfLastMonth = startOfMonth.minusMonths(1);

        var current = reportCalculationService.computeFigures(tenantId, startOfMonth, startOfNextMonth, zone);
        var previous = reportCalculationService.computeFigures(tenantId, startOfLastMonth, startOfMonth, zone);

        record Candidate(String key, BigDecimal previousValue, BigDecimal currentValue, Object[] extra) {}
        List<Candidate> candidates = new ArrayList<>();
        candidates.add(new Candidate("MAINTENANCE_COST_CHANGE", previous.maintenance().totalCost(), current.maintenance().totalCost(),
                new Object[]{current.maintenance().highestCostOrder() != null ? current.maintenance().highestCostOrder().label() : null}));
        candidates.add(new Candidate("REVENUE_CHANGE", previous.financial().grossRevenue(), current.financial().grossRevenue(), new Object[0]));
        candidates.add(new Candidate("OUTSTANDING_CHANGE", previous.financial().outstandingBalance(), current.financial().outstandingBalance(), new Object[0]));

        Candidate mostNotable = candidates.stream()
                .filter(c -> c.previousValue().signum() != 0)
                .max(Comparator.comparing(c -> c.currentValue().subtract(c.previousValue()).abs()
                        .divide(c.previousValue().abs(), 4, java.math.RoundingMode.HALF_UP)))
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        if (mostNotable == null) {
            result.put("available", false);
            return result;
        }
        BigDecimal percent = mostNotable.currentValue().subtract(mostNotable.previousValue())
                .divide(mostNotable.previousValue().abs(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP);
        result.put("available", true);
        result.put("key", mostNotable.key());
        result.put("percentChange", percent);
        result.put("previousValue", mostNotable.previousValue());
        result.put("currentValue", mostNotable.currentValue());
        result.put("relatedLabel", mostNotable.extra().length > 0 ? mostNotable.extra()[0] : null);
        return result;
    }

    private Map<String, Object> financialControlCenter(Long tenantId, ZoneId zone, LocalDate today) {
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime startOfYesterday = today.minusDays(1).atStartOfDay();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);
        LocalDateTime startOfLastMonth = startOfMonth.minusMonths(1);
        LocalDateTime startOfYear = today.withDayOfYear(1).atStartOfDay();

        ReportCalculationService.PeriodFigures todayFigures = reportCalculationService.computeFigures(tenantId, startOfToday, startOfTomorrow, zone);
        ReportCalculationService.PeriodFigures yesterdayFigures = reportCalculationService.computeFigures(tenantId, startOfYesterday, startOfToday, zone);
        ReportCalculationService.PeriodFigures monthFigures = reportCalculationService.computeFigures(tenantId, startOfMonth, startOfNextMonth, zone);
        ReportCalculationService.PeriodFigures lastMonthFigures = reportCalculationService.computeFigures(tenantId, startOfLastMonth, startOfMonth, zone);
        ReportCalculationService.PeriodFigures yearFigures = reportCalculationService.computeFigures(tenantId, startOfYear, startOfTomorrow, zone);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collectedToday", todayFigures.financial().collectedPayments());
        result.put("collectedThisMonth", monthFigures.financial().collectedPayments());
        result.put("unpaidBalance", monthFigures.financial().outstandingBalance());
        result.put("overdueBalance", monthFigures.financial().overduePayments());
        result.put("refunds", monthFigures.financial().refunds());
        result.put("maintenanceExpenses", monthFigures.maintenance().totalCost());
        result.put("netProfitEstimate", monthFigures.financial().profit());
        result.put("averageRentalValue", monthFigures.financial().avgRevenuePerRental());
        result.put("yearToDateRevenue", yearFigures.financial().grossRevenue());

        result.put("vsYesterday", changeMap(yesterdayFigures.financial().collectedPayments(), todayFigures.financial().collectedPayments()));
        result.put("vsLastMonth", changeMap(lastMonthFigures.financial().grossRevenue(), monthFigures.financial().grossRevenue()));
        return result;
    }

    private Map<String, Object> changeMap(BigDecimal previous, BigDecimal current) {
        var change = com.carrental.dto.reporting.ReportDataset.MetricChange.of(previous, current);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("previous", change.previousValue());
        m.put("current", change.currentValue());
        m.put("absoluteChange", change.absoluteChange());
        m.put("percentChange", change.percentChange());
        m.put("percentAvailable", change.percentAvailable());
        return m;
    }

    // ── 4. Vehicle Profitability ──────────────────────────────────────────────

    private Map<String, Object> vehicleProfitability(Long tenantId, ZoneId zone, LocalDate today) {
        LocalDateTime start = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<VehiclePerformance> all = reportCalculationService.computeFigures(tenantId, start, end, zone).allVehiclePerformance();
        if (all.isEmpty()) return Map.of("hasData", false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasData", true);
        result.put("topRevenue", vehicleFrom(all.stream().max(Comparator.comparing(VehiclePerformance::revenue))));
        result.put("bestUtilization", vehicleFrom(all.stream().max(Comparator.comparing(VehiclePerformance::utilizationRate))));
        result.put("highestMaintenanceCost", vehicleFrom(all.stream().max(Comparator.comparing(VehiclePerformance::expenses))));
        result.put("lowestUtilization", vehicleFrom(all.stream().min(Comparator.comparing(VehiclePerformance::utilizationRate))));
        result.put("negativeContribution", vehicleFrom(all.stream().filter(v -> v.profitContribution().signum() < 0)
                .min(Comparator.comparing(VehiclePerformance::profitContribution))));
        return result;
    }

    private Map<String, Object> vehicleFrom(Optional<VehiclePerformance> vp) {
        if (vp.isEmpty()) return null;
        VehiclePerformance v = vp.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vehicleId", v.vehicleId());
        m.put("label", v.label());
        m.put("revenue", v.revenue());
        m.put("expenses", v.expenses());
        m.put("profitContribution", v.profitContribution());
        m.put("utilizationRate", v.utilizationRate());
        return m;
    }

    // ── 5. Payment Risk ────────────────────────────────────────────────────────

    private Map<String, Object> paymentRisk(List<Contract> contracts, LocalDate today) {
        List<Contract> withDebt = contracts.stream()
                .filter(c -> c.getStatus() != ContractStatus.CANCELLED)
                .filter(c -> c.getRemainingAmount() != null && c.getRemainingAmount().signum() > 0)
                .toList();
        BigDecimal totalOverdue = withDebt.stream()
                .filter(c -> c.getEndDate() != null && c.getEndDate().isBefore(today))
                .map(Contract::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long clientsWithDebt = withDebt.stream().map(c -> c.getClient() != null ? c.getClient().getId() : null)
                .filter(Objects::nonNull).distinct().count();
        long partialPayment = withDebt.stream().filter(c -> "PARTIAL".equalsIgnoreCase(c.getPaymentStatus())).count();
        long dueThisWeek = withDebt.stream()
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isBefore(today) && !c.getEndDate().isAfter(today.plusDays(7)))
                .count();
        Contract highest = withDebt.stream().max(Comparator.comparing(Contract::getRemainingAmount)).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOverdue", totalOverdue);
        result.put("clientsWithDebt", clientsWithDebt);
        result.put("contractsWithPartialPayment", partialPayment);
        result.put("dueThisWeek", dueThisWeek);
        if (highest != null) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("contractId", highest.getId());
            h.put("contractNumber", highest.getContractNumber());
            h.put("clientName", contractClientName(highest));
            h.put("amount", highest.getRemainingAmount());
            result.put("highestUnpaidContract", h);
        }
        return result;
    }

    // ── 6. Maintenance Intelligence ───────────────────────────────────────────

    private Map<String, Object> maintenanceIntelligence(List<VehicleMaintenance> maintenance, LocalDate today) {
        LocalDate soon = today.plusDays(7);
        long dueSoon = maintenance.stream().filter(m -> m.getStatus() == MaintenanceStatus.SCHEDULED
                && m.getScheduledAt() != null && !m.getScheduledAt().toLocalDate().isBefore(today)
                && !m.getScheduledAt().toLocalDate().isAfter(soon)).count();
        long overdue = maintenance.stream().filter(m -> m.getStatus() == MaintenanceStatus.SCHEDULED
                && m.getScheduledAt() != null && m.getScheduledAt().toLocalDate().isBefore(today)).count();
        long inProgress = maintenance.stream().filter(m -> m.getStatus() == MaintenanceStatus.IN_PROGRESS).count();
        long planned = maintenance.stream().filter(m -> m.getStatus() == MaintenanceStatus.SCHEDULED).count();

        LocalDate monthStart = today.withDayOfMonth(1);
        List<VehicleMaintenance> completedThisMonth = maintenance.stream()
                .filter(m -> m.getStatus() == MaintenanceStatus.COMPLETED && m.getCompletedAt() != null
                        && !m.getCompletedAt().toLocalDate().isBefore(monthStart))
                .toList();
        BigDecimal totalCostThisMonth = completedThisMonth.stream()
                .map(m -> m.getCost() != null ? m.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, Long> byVehicle = new HashMap<>();
        Map<Long, BigDecimal> costByVehicle = new HashMap<>();
        Map<Long, String> labelByVehicle = new HashMap<>();
        for (VehicleMaintenance m : maintenance) {
            if (m.getVehicle() == null) continue;
            Long vId = m.getVehicle().getId();
            byVehicle.merge(vId, 1L, Long::sum);
            costByVehicle.merge(vId, m.getCost() != null ? m.getCost() : BigDecimal.ZERO, BigDecimal::add);
            labelByVehicle.putIfAbsent(vId, vehicleLabel(m.getVehicle()));
        }
        long repeatedProblems = byVehicle.values().stream().filter(count -> count >= 3).count();
        Map.Entry<Long, BigDecimal> mostExpensive = costByVehicle.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dueSoon", dueSoon);
        result.put("overdue", overdue);
        result.put("inProgress", inProgress);
        result.put("planned", planned);
        result.put("completedThisMonth", completedThisMonth.size());
        result.put("totalCostThisMonth", totalCostThisMonth);
        result.put("repeatedProblems", repeatedProblems);
        if (mostExpensive != null) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("vehicleId", mostExpensive.getKey());
            v.put("label", labelByVehicle.get(mostExpensive.getKey()));
            v.put("cost", mostExpensive.getValue());
            result.put("mostExpensiveVehicle", v);
        }
        return result;
    }

    // ── 7. Fleet health detail (counts + percentages, clickable filters) ────────

    /** Above this, a vehicle is flagged "high mileage" for preventive-maintenance visibility — not a hard limit. */
    private static final int HIGH_MILEAGE_THRESHOLD_KM = 150_000;

    private Map<String, Object> fleetHealth(List<Vehicle> vehicles, List<VehicleMaintenance> maintenance, LocalDate today) {
        int total = vehicles.size();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("available", vehicles.stream().filter(v -> v.getStatut() == VehicleStatus.AVAILABLE).count());
        counts.put("reserved", vehicles.stream().filter(v -> v.getStatut() == VehicleStatus.RESERVED).count());
        counts.put("rented", vehicles.stream().filter(v -> v.getStatut() == VehicleStatus.RENTED).count());
        counts.put("maintenance", vehicles.stream().filter(v -> v.getStatut() == VehicleStatus.IN_MAINTENANCE || v.getStatut() == VehicleStatus.MAINTENANCE).count());
        counts.put("inactive", vehicles.stream().filter(v -> v.getStatut() == VehicleStatus.OUT_OF_SERVICE
                || v.getStatut() == VehicleStatus.ARCHIVED || v.getStatut() == VehicleStatus.SOLD).count());
        counts.put("documentExpired", vehicles.stream().filter(v ->
                (v.getInsuranceExpiration() != null && v.getInsuranceExpiration().isBefore(today))
                        || (v.getTechnicalInspectionExpiration() != null && v.getTechnicalInspectionExpiration().isBefore(today))).count());
        Set<Long> overdueMaintenanceVehicleIds = maintenance.stream()
                .filter(m -> m.getStatus() == MaintenanceStatus.SCHEDULED && m.getScheduledAt() != null
                        && m.getScheduledAt().toLocalDate().isBefore(today))
                .map(m -> m.getVehicle() != null ? m.getVehicle().getId() : null)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        counts.put("maintenanceOverdue", (long) overdueMaintenanceVehicleIds.size());
        counts.put("highMileage", vehicles.stream().filter(v -> v.getMileageCurrent() != null && v.getMileageCurrent() > HIGH_MILEAGE_THRESHOLD_KM).count());
        counts.put("gpsOffline", vehicles.stream().filter(v -> Boolean.TRUE.equals(v.getGpsEnabled()) && v.getGpsStatus() == GpsDeviceStatus.OFFLINE).count());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        counts.forEach((key, count) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("count", count);
            entry.put("percent", total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(count)
                    .divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP));
            breakdown.put(key, entry);
        });
        result.put("breakdown", breakdown);
        return result;
    }

    // ── 8. Reservation funnel ────────────────────────────────────────────────

    private Map<String, Object> reservationFunnel(List<Reservation> reservations) {
        long pending = reservations.stream().filter(r -> r.getStatus() == ReservationStatus.PENDING).count();
        long confirmed = reservations.stream().filter(r -> r.getStatus() == ReservationStatus.CONFIRMED).count();
        long convertedToContract = reservations.stream().filter(r -> r.getStatus() == ReservationStatus.CONVERTED_TO_CONTRACT || r.getContract() != null).count();
        long completed = reservations.stream().filter(r -> r.getStatus() == ReservationStatus.COMPLETED).count();
        long cancelled = reservations.stream().filter(r -> r.getStatus() == ReservationStatus.CANCELLED).count();
        long totalNonCancelled = reservations.size() - cancelled;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", pending);
        result.put("confirmed", confirmed);
        result.put("convertedToContract", convertedToContract);
        result.put("completed", completed);
        result.put("cancelled", cancelled);
        result.put("pendingToConfirmedRate", conversionRate(pending + confirmed, confirmed + convertedToContract + completed));
        result.put("confirmedToContractRate", conversionRate(confirmed + convertedToContract + completed, convertedToContract + completed));
        result.put("contractToCompletedRate", conversionRate(convertedToContract + completed, completed));
        result.put("total", totalNonCancelled);
        return result;
    }

    /** Never a misleading percentage: null (not zero, not 100) when the denominator itself is zero. */
    private BigDecimal conversionRate(long denominator, long numerator) {
        if (denominator <= 0) return null;
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP);
    }

    // ── 9. Contract pipeline ─────────────────────────────────────────────────

    private Map<String, Object> contractPipeline(List<Contract> contracts, LocalDate today) {
        Map<String, Long> pipeline = new LinkedHashMap<>();
        pipeline.put("draft", contracts.stream().filter(c -> c.getStatus() == ContractStatus.DRAFT).count());
        pipeline.put("waitingSignature", contracts.stream().filter(c -> WAITING_SIGNATURE_STATUSES.contains(c.getStatus())).count());
        pipeline.put("active", contracts.stream().filter(c -> LIVE_CONTRACT_STATUSES.contains(c.getStatus())).count());
        pipeline.put("endingToday", contracts.stream().filter(c -> LIVE_CONTRACT_STATUSES.contains(c.getStatus())
                && today.equals(c.getEndDate())).count());
        pipeline.put("overdueReturn", contracts.stream().filter(c -> LIVE_CONTRACT_STATUSES.contains(c.getStatus())
                && c.getEndDate() != null && c.getEndDate().isBefore(today)).count());
        pipeline.put("completed", contracts.stream().filter(c -> c.getStatus() == ContractStatus.COMPLETED).count());
        pipeline.put("unpaid", contracts.stream().filter(c -> c.getStatus() != ContractStatus.CANCELLED
                && c.getRemainingAmount() != null && c.getRemainingAmount().signum() > 0).count());
        return new LinkedHashMap<>(pipeline);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String clientName(Client c) { return c != null ? c.getName() : null; }

    private String contractClientName(Contract c) {
        return c.getClientFullName() != null ? c.getClientFullName() : c.getClientName();
    }

    private String vehicleLabel(Vehicle v) {
        if (v == null) return null;
        String plate = v.getPlate() != null ? " (" + v.getPlate() + ")" : "";
        return (v.getMarque() != null ? v.getMarque() : "Vehicle") + plate;
    }

    private String contractVehicleLabel(Contract c) {
        if (c.getVehicle() != null) return vehicleLabel(c.getVehicle());
        return c.getVehicleBrand();
    }

    private String contractLabel(Contract c) {
        return "contract " + c.getContractNumber() + (contractClientName(c) != null ? " (" + contractClientName(c) + ")" : "");
    }

    private <T> T safe(String label, T fallback, Supplier<T> supplier) {
        try {
            T value = supplier.get();
            return value != null ? value : fallback;
        } catch (Exception ex) {
            log.warn("Dashboard intelligence section '{}' failed for tenant [{}]: {}", label, TenantContext.getCurrentTenantId(), ex.getMessage());
            return fallback;
        }
    }
}
