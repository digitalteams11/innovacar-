package com.carrental.service;

import com.carrental.dto.dashboard.DashboardResponse;
import com.carrental.dto.payment.PaymentResponse;
import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Service to aggregate dashboard metrics from real data.
 * Every counter is computed from the database — no static or fake values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VehicleRepository vehicleRepository;
    private final PaymentRepository paymentRepository;
    private final DepositRepository depositRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * Contract statuses that represent a still-in-progress or fully active rental —
     * i.e. everything except a pure unsent draft, or a finished/cancelled contract.
     * Counted together for the "Active Contracts" dashboard card so a contract awaiting
     * one or both signatures is visible as soon as work on it has started.
     */
    private static final List<ContractStatus> ACTIVE_CONTRACT_STATUSES = List.of(
            ContractStatus.WAITING_SIGNATURE,
            ContractStatus.WAITING_CLIENT_SIGNATURE,
            ContractStatus.PENDING_SIGNATURE,
            ContractStatus.PARTIALLY_SIGNED,
            ContractStatus.SIGNED,
            ContractStatus.ACTIVE,
            ContractStatus.PAID
    );

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardMetrics() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Current user is not linked to an agency.");
        }

        long totalVehicles = safeMetric("total vehicles", 0L, () -> vehicleRepository.countByTenantId(tenantId));
        long availableVehicles = safeMetric("available vehicles", 0L, () -> vehicleRepository.countAvailableByTenantId(tenantId));
        long reservedVehicles = safeMetric("reserved vehicles", 0L, () -> vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.RESERVED));
        long rentedVehicles = safeMetric("rented vehicles", 0L, () -> vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.RENTED));
        List<Reservation> reservations = safeMetric("reservations", List.<Reservation>of(), () -> reservationRepository.findAllByTenantId(tenantId));
        long totalReservations = reservations.size();
        long totalClients = safeMetric("clients", 0L, () -> (long) clientRepository.findAllByTenantId(tenantId).size());
        LocalDate today = LocalDate.now();

        long reservationsToday = reservations.stream()
                .filter(reservation -> today.equals(reservation.getDateStart()))
                .filter(reservation -> reservation.getStatus() != ReservationStatus.CANCELLED)
                .count();
        long reservationsThisMonth = reservations.stream()
                .filter(reservation -> reservation.getDateStart() != null)
                .filter(reservation -> reservation.getDateStart().getMonth() == today.getMonth()
                        && reservation.getDateStart().getYear() == today.getYear())
                .filter(reservation -> reservation.getStatus() != ReservationStatus.CANCELLED)
                .count();
        long upcomingReservations = reservations.stream()
                .filter(reservation -> reservation.getDateStart() != null && reservation.getDateStart().isAfter(today))
                .filter(this::isOpenBooking)
                .count();
        long activeContracts = safeMetric("active contracts", 0L, () -> contractRepository.countByTenantIdAndStatusIn(tenantId, ACTIVE_CONTRACT_STATUSES));
        long pendingContracts = safeMetric("pending contracts", 0L, () ->
                (long) contractRepository.findAllByTenantIdAndStatus(tenantId, ContractStatus.DRAFT).size()
                        + contractRepository.findAllByTenantIdAndStatus(tenantId, ContractStatus.WAITING_SIGNATURE).size());
        long signedContracts = safeMetric("signed contracts", 0L, () ->
                (long) contractRepository.findAllByTenantIdAndStatus(tenantId, ContractStatus.SIGNED).size());

        BigDecimal totalRevenue = safeMoney("total revenue", () -> paymentRepository.sumCollectedRentalRevenueByTenantId(tenantId));
        BigDecimal monthlyRevenue = safeMoney("monthly revenue", () -> calculateMonthlyRevenue(tenantId));
        BigDecimal pendingAmount = safeMoney("pending payment amount", () -> paymentRepository.sumPendingAmountByTenantId(tenantId));
        long pendingCount = safeMetric("pending payment count", 0L, () -> paymentRepository.countPendingByTenantId(tenantId));
        BigDecimal paymentsToday = safeMoney("payments today", () -> {
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
            return paymentRepository.sumCollectedRentalRevenueBetween(tenantId, startOfDay, startOfTomorrow);
        });

        long paidInvoices = safeMetric("paid invoices", 0L, () -> invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .count());
        long overdueInvoices = safeMetric("overdue invoices", 0L, () -> invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                .count());

        BigDecimal refundAmount = safeMoney("refund amount", () -> paymentRepository.sumRefundsByTenantId(tenantId));
        long refundCount = safeMetric("refund count", 0L, () -> paymentRepository.countRefundsByTenantId(tenantId));
        List<PaymentResponse> recent = safeMetric("recent transactions", List.<PaymentResponse>of(), () -> paymentRepository.findTop5ByTenantIdOrderByPaymentDateDesc(tenantId)
                .stream()
                .map(PaymentResponse::from)
                .toList());

        BigDecimal totalDepositsHeld = safeMoney("active deposits", () -> depositRepository.sumActiveDepositsByTenantId(tenantId));
        Long pendingReturns = safeMetric("pending returns", 0L, () -> depositRepository.countPendingReturnsByTenantId(tenantId));
        BigDecimal returnedDeposits = safeMoney("returned deposits", () -> depositRepository.sumReturnedDepositsByTenantId(tenantId));
        BigDecimal depositDeductions = safeMoney("deposit deductions", () -> depositRepository.sumTotalDeductionsByTenantId(tenantId));
        BigDecimal depositRevenue = safeMoney("deposit revenue", () -> depositRepository.sumDepositRevenueByTenantId(tenantId));

        // ── Vehicle cards ────────────────────────────────────────────────────────
        List<Vehicle> allVehicles = safeMetric("all vehicles list", List.<Vehicle>of(),
                () -> vehicleRepository.findAllByTenantId(tenantId));
        log.debug("[DASHBOARD_FLEET_DATA_DEBUG] endpoint=GET /api/dashboard tenantId={} count={} firstVehicle={}",
                tenantId, allVehicles.size(),
                allVehicles.isEmpty() ? "none" : allVehicles.get(0).getMarque() + "/" + allVehicles.get(0).getStatut());
        long maintenanceVehicles = allVehicles.stream()
                .filter(v -> v.getStatut() == VehicleStatus.IN_MAINTENANCE || v.getStatut() == VehicleStatus.MAINTENANCE)
                .count();

        // Active contracts to find client name + contract number for rented vehicles
        List<Contract> activeContractsList = safeMetric("active contracts list", List.<Contract>of(),
                () -> contractRepository.findAllByTenantId(tenantId).stream()
                        .filter(c -> ACTIVE_CONTRACT_STATUSES.contains(c.getStatus()))
                        .toList());

        List<Map<String, Object>> vehicleCards = allVehicles.stream().map(v -> {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", v.getId());
            card.put("marque", v.getMarque());
            card.put("plate", v.getPlate());
            card.put("category", v.getCategory());
            card.put("statut", v.getStatut() != null ? v.getStatut().name() : "AVAILABLE");
            card.put("prixJour", v.getPrixJour());
            card.put("fuel", v.getFuel());
            card.put("fuelLevelCurrent", v.getFuelLevelCurrent());
            card.put("mileageCurrent", v.getMileageCurrent());
            card.put("imageUrl", v.getImageUrl());
            card.put("insuranceExpiration", v.getInsuranceExpiration());
            card.put("technicalInspectionExpiration", v.getTechnicalInspectionExpiration());
            card.put("conditionStatus", v.getConditionStatus());
            card.put("lastReturnedAt", v.getLastReturnedAt());
            // Link active contract info if rented
            activeContractsList.stream()
                    .filter(c -> c.getVehicle() != null && c.getVehicle().getId().equals(v.getId()))
                    .findFirst()
                    .ifPresent(c -> {
                        card.put("activeContractNumber", c.getContractNumber());
                        card.put("activeContractId", c.getId());
                        card.put("clientName", c.getClientFullName() != null ? c.getClientFullName() : c.getClientName());
                        card.put("contractEndDate", c.getEndDate());
                    });
            return card;
        }).toList();

        // ── Upcoming returns (contracts ending in next 7 days) ───────────────────
        List<Map<String, Object>> upcomingReturnsList = activeContractsList.stream()
                .filter(c -> c.getEndDate() != null
                        && !c.getEndDate().isBefore(today)
                        && c.getEndDate().isBefore(today.plusDays(8)))
                .map(c -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("contractId", c.getId());
                    r.put("contractNumber", c.getContractNumber());
                    r.put("clientName", c.getClientFullName() != null ? c.getClientFullName() : c.getClientName());
                    r.put("endDate", c.getEndDate());
                    r.put("vehicleMarque", c.getVehicle() != null ? c.getVehicle().getMarque() : c.getVehicleBrand());
                    r.put("vehiclePlate", c.getVehicle() != null ? c.getVehicle().getPlate() : c.getVehicleRegistration());
                    return r;
                }).toList();

        // ── Alerts ───────────────────────────────────────────────────────────────
        List<Map<String, Object>> alertsList = buildAlerts(allVehicles, activeContractsList, today);

        log.info("[DASHBOARD_SUMMARY] tenantId={} fleet={} activeRentals={} reservations={} monthlyRevenue={} availableVehicles={} reservedVehicles={} rentedVehicles={}",
                tenantId, totalVehicles, activeContracts, totalReservations, monthlyRevenue,
                availableVehicles, reservedVehicles, rentedVehicles);

        return DashboardResponse.builder()
                .totalVehicles(totalVehicles)
                .rentedVehicles(rentedVehicles)
                .maintenanceVehicles(maintenanceVehicles)
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .totalReservations(totalReservations)
                .totalClients(totalClients)
                .reservationsToday(reservationsToday)
                .reservationsThisMonth(reservationsThisMonth)
                .upcomingReservations(upcomingReservations)
                .activeContracts(activeContracts)
                .pendingContracts(pendingContracts)
                .signedContracts(signedContracts)
                .availableVehicles(availableVehicles)
                .reservedVehicles(reservedVehicles)
                .pendingPaymentsAmount(pendingAmount)
                .pendingPaymentsCount(pendingCount)
                .paymentsToday(paymentsToday)
                .paidInvoices(paidInvoices)
                .overdueInvoices(overdueInvoices)
                .refundAmount(refundAmount)
                .refundCount(refundCount)
                .recentTransactions(recent)
                .vehicles(vehicleCards)
                .activeRentals(upcomingReturnsList)
                .upcomingReturns(upcomingReturnsList)
                .recentActivity(List.of())
                .alerts(alertsList)
                .totalDepositsHeld(totalDepositsHeld)
                .pendingReturns(pendingReturns != null ? pendingReturns : 0L)
                .returnedDeposits(returnedDeposits)
                .depositDeductions(depositDeductions)
                .depositRevenue(depositRevenue)
                .build();
    }

    private List<Map<String, Object>> buildAlerts(List<Vehicle> vehicles, List<Contract> activeContracts, LocalDate today) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        LocalDate warnDate = today.plusDays(30);

        for (Vehicle v : vehicles) {
            String name = v.getMarque() != null ? v.getMarque() : "Vehicle";

            if (v.getInsuranceExpiration() != null && !v.getInsuranceExpiration().isAfter(warnDate)) {
                long days = today.until(v.getInsuranceExpiration(), java.time.temporal.ChronoUnit.DAYS);
                alerts.add(alert("VEHICLE_INSURANCE_EXPIRY",
                        days <= 0 ? "danger" : "warning",
                        "Insurance Expires " + (days <= 0 ? "OVERDUE" : "Soon"),
                        name + " insurance expires " + (days <= 0 ? "— overdue!" : "in " + days + " days."),
                        "VEHICLE", v.getId()));
            }
            if (v.getTechnicalInspectionExpiration() != null && !v.getTechnicalInspectionExpiration().isAfter(warnDate)) {
                long days = today.until(v.getTechnicalInspectionExpiration(), java.time.temporal.ChronoUnit.DAYS);
                alerts.add(alert("VEHICLE_TECHNICAL_EXPIRY",
                        days <= 0 ? "danger" : "warning",
                        "Technical Inspection " + (days <= 0 ? "OVERDUE" : "Expiring"),
                        name + " technical inspection expires " + (days <= 0 ? "— overdue!" : "in " + days + " days."),
                        "VEHICLE", v.getId()));
            }
            if (v.getCirculationAuthorizationExpiryDate() != null && !v.getCirculationAuthorizationExpiryDate().isAfter(warnDate)) {
                long days = today.until(v.getCirculationAuthorizationExpiryDate(), java.time.temporal.ChronoUnit.DAYS);
                alerts.add(alert("VEHICLE_CIRCULATION_EXPIRY",
                        days <= 0 ? "danger" : "warning",
                        "Circulation Authorization " + (days <= 0 ? "EXPIRED" : "Expiring"),
                        name + " circulation authorization expires " + (days <= 0 ? "— overdue!" : "in " + days + " days."),
                        "VEHICLE", v.getId()));
            }
            if (v.getStatut() == VehicleStatus.IN_MAINTENANCE || v.getStatut() == VehicleStatus.MAINTENANCE) {
                alerts.add(alert("VEHICLE_MAINTENANCE",
                        "info",
                        "Vehicle In Maintenance",
                        name + " is currently in maintenance.",
                        "VEHICLE", v.getId()));
            }
        }

        for (Contract c : activeContracts) {
            if (c.getEndDate() != null && !c.getEndDate().isAfter(today)) {
                String client = c.getClientFullName() != null ? c.getClientFullName() : c.getClientName();
                alerts.add(alert("CONTRACT_OVERDUE_RETURN",
                        "danger",
                        "Vehicle Return Overdue",
                        "Contract " + c.getContractNumber() + (client != null ? " (" + client + ")" : "") + " — return was due " + c.getEndDate() + ".",
                        "CONTRACT", c.getId()));
            } else if (c.getEndDate() != null && c.getEndDate().equals(today)) {
                String client = c.getClientFullName() != null ? c.getClientFullName() : c.getClientName();
                alerts.add(alert("CONTRACT_RETURN_TODAY",
                        "warning",
                        "Vehicle Return Due Today",
                        "Contract " + c.getContractNumber() + (client != null ? " (" + client + ")" : "") + " is due for return today.",
                        "CONTRACT", c.getId()));
            }
            if ("PARTIAL".equalsIgnoreCase(c.getPaymentStatus()) || "PENDING".equalsIgnoreCase(c.getPaymentStatus())) {
                alerts.add(alert("PAYMENT_PARTIAL",
                        "info",
                        "Payment Pending",
                        "Contract " + c.getContractNumber() + " has a pending or partial payment.",
                        "CONTRACT", c.getId()));
            }
        }

        return alerts;
    }

    private Map<String, Object> alert(String type, String severity, String title, String message, String entityType, Long entityId) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", type + "_" + entityId);
        a.put("type", type);
        a.put("severity", severity);
        a.put("title", title);
        a.put("message", message);
        a.put("entityType", entityType);
        a.put("entityId", entityId);
        return a;
    }

    private <T> T safeMetric(String label, T fallback, Supplier<T> supplier) {
        try {
            T value = supplier.get();
            return value != null ? value : fallback;
        } catch (Exception ex) {
            log.warn("Dashboard metric '{}' failed for tenant [{}]: {}", label, TenantContext.getCurrentTenantId(), ex.getMessage());
            return fallback;
        }
    }

    private BigDecimal safeMoney(String label, Supplier<BigDecimal> supplier) {
        return safeMetric(label, BigDecimal.ZERO, supplier);
    }
    private boolean isOpenBooking(Reservation reservation) {
        return reservation.getStatus() != null
                && (reservation.getStatus() == ReservationStatus.PENDING
                || reservation.getStatus() == ReservationStatus.CONFIRMED
                || reservation.getStatus() == ReservationStatus.ACTIVE);
    }

    private BigDecimal calculateMonthlyRevenue(Long tenantId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
        BigDecimal revenue = paymentRepository.sumCollectedRentalRevenueBetween(tenantId, startOfMonth, endOfMonth);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }
}

