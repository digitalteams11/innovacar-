package com.carrental.controller;

import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Reports REST controller.
 *
 * <pre>
 * GET /api/reports/revenue            – revenue by month         [authenticated]
 * GET /api/reports/vehicle-utilization – utilization stats       [authenticated]
 * GET /api/reports/client-activity    – client stats             [authenticated]
 * </pre>
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final InvoiceRepository     invoiceRepository;
    private final ContractRepository    contractRepository;
    private final VehicleRepository     vehicleRepository;
    private final ClientRepository      clientRepository;
    private final ReservationRepository reservationRepository;

    // ── GET /api/reports/revenue ─────────────────────────────────────────────

    /**
     * Returns monthly revenue data for charting.
     */
    @GetMapping("/revenue")
    public ResponseEntity<List<Map<String, Object>>> getRevenue(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        Long tenantId = TenantContext.getCurrentTenantId();

        LocalDate today = LocalDate.now();
        LocalDate rangeStart = startDate != null ? startDate : today.minusMonths(5).withDayOfMonth(1);
        LocalDate rangeEnd = endDate != null ? endDate : today;
        if (rangeEnd.isBefore(rangeStart)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }

        Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();
        YearMonth cursor = YearMonth.from(rangeStart);
        YearMonth last = YearMonth.from(rangeEnd);
        while (!cursor.isAfter(last)) {
            revenueByMonth.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusMonths(1);
        }

        invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.PAID)
                .filter(invoice -> invoice.getIssueDate() != null)
                .filter(invoice -> !invoice.getIssueDate().isBefore(rangeStart)
                        && !invoice.getIssueDate().isAfter(rangeEnd))
                .forEach(invoice -> {
                    YearMonth month = YearMonth.from(invoice.getIssueDate());
                    BigDecimal current = revenueByMonth.getOrDefault(month, BigDecimal.ZERO);
                    revenueByMonth.put(month, current.add(invoice.getAmount() == null ? BigDecimal.ZERO : invoice.getAmount()));
                });

        List<Map<String, Object>> result = revenueByMonth.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "name", entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        "month", entry.getKey().toString(),
                        "revenue", entry.getValue()))
                .toList();

        return ResponseEntity.ok(result);
    }

    // ── GET /api/reports/vehicle-utilization ─────────────────────────────────

    /**
     * Returns vehicle utilization stats for charting.
     */
    @GetMapping("/vehicle-utilization")
    public ResponseEntity<List<Map<String, Object>>> getVehicleUtilization() {
        Long tenantId = TenantContext.getCurrentTenantId();

        long available = vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.AVAILABLE);
        long reserved = vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.RESERVED);
        long rented = vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.RENTED);
        long maintenance = vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.MAINTENANCE);
        long outOfService = vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.OUT_OF_SERVICE);

        List<Map<String, Object>> result = new ArrayList<>();
        if (available > 0) result.add(Map.of("name", "Available", "value", available));
        if (reserved > 0) result.add(Map.of("name", "Reserved", "value", reserved));
        if (rented > 0) result.add(Map.of("name", "Rented", "value", rented));
        if (maintenance > 0) result.add(Map.of("name", "Maintenance", "value", maintenance));
        if (outOfService > 0) result.add(Map.of("name", "Out of Service", "value", outOfService));

        return ResponseEntity.ok(result);
    }

    // ── GET /api/reports/client-activity ─────────────────────────────────────

    /**
     * Returns client activity stats for the caller's tenant.
     */
    @GetMapping("/client-activity")
    public ResponseEntity<Map<String, Long>> getClientActivity() {
        Long tenantId = TenantContext.getCurrentTenantId();

        long totalClients   = clientRepository.findAllByTenantId(tenantId).size();
        long activeContracts = contractRepository.findAllByTenantId(tenantId).stream()
                .filter(c -> c.getStatus().name().equals("ACTIVE") || c.getStatus().name().equals("PENDING"))
                .count();

        return ResponseEntity.ok(Map.of(
                "totalClients", totalClients,
                "activeContracts", activeContracts
        ));
    }
}
