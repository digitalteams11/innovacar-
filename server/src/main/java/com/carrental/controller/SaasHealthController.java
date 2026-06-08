package com.carrental.controller;

import com.carrental.entity.Tenant;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/saas-health")
@RequiredArgsConstructor
public class SaasHealthController {

    private final TenantRepository tenantRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealthScore() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        int vehicles = (int) Math.min(vehicleRepository.countByTenantId(tenantId), 10);
        int reservations = Math.min(reservationRepository.findAllByTenantId(tenantId).size(), 20);
        int contracts = Math.min(contractRepository.findAllByTenantId(tenantId).size(), 20);
        int employees = Math.min(employeeRepository.findAllByTenantId(tenantId).size(), 5);
        int gps = (int) Math.min(vehicleRepository.findAllByTenantId(tenantId).stream().filter(v -> v.getGpsDeviceId() != null).count(), 10);

        int score = Math.min(100, vehicles * 2 + reservations * 2 + contracts * 2 + employees * 3 + gps);
        String label = score <= 30 ? "Poor" : score <= 60 ? "Average" : score <= 80 ? "Good" : "Excellent";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("label", label);
        result.put("vehiclesAdded", vehicles);
        result.put("reservationsCreated", reservations);
        result.put("contractsSigned", contracts);
        result.put("gpsConnected", gps);
        result.put("employeeActivity", employees);
        result.put("risk", riskStatus(tenant, score));
        return ResponseEntity.ok(result);
    }

    private String riskStatus(Tenant tenant, int score) {
        if (tenant.getSubscriptionEndDate() != null && ChronoUnit.DAYS.between(LocalDate.now(), tenant.getSubscriptionEndDate()) <= 7) {
            return "SUBSCRIPTION_EXPIRES_SOON";
        }
        if (score < 20) return "USAGE_BELOW_20_PERCENT";
        return "HEALTHY";
    }
}
