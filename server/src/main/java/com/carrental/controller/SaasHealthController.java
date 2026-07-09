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
    private final ClientRepository clientRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealthScore() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return ResponseEntity.ok(withEnvelope(defaultHealth()));
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.ok(withEnvelope(defaultHealth()));
        }
        long vehicles = vehicleRepository.countByTenantId(tenantId);
        long reservations = reservationRepository.findAllByTenantId(tenantId).size();
        long contracts = contractRepository.findAllByTenantId(tenantId).size();
        long employees = employeeRepository.findAllByTenantId(tenantId).size();
        long clients = clientRepository.countByTenantId(tenantId);
        long gps = vehicleRepository.findAllByTenantId(tenantId).stream().filter(v -> v.getGpsDeviceId() != null).count();

        int completedSteps = 1;
        if (vehicles > 0) completedSteps++;
        if (clients > 0) completedSteps++;
        if (reservations > 0) completedSteps++;
        if (contracts > 0) completedSteps++;
        int score = completedSteps * 20;
        String label = score <= 20 ? "Getting Started" : score <= 60 ? "Growing" : score <= 80 ? "Good" : "Excellent";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("label", label);
        result.put("vehiclesAdded", vehicles);
        result.put("reservationsCreated", reservations);
        result.put("contractsSigned", contracts);
        result.put("gpsConnected", gps);
        result.put("employeeActivity", employees);
        result.put("risk", riskStatus(tenant, score));
        result.put("completedSteps", completedSteps);
        result.put("totalSteps", 5);
        result.put("steps", healthSteps(vehicles > 0, clients > 0, reservations > 0, contracts > 0));
        return ResponseEntity.ok(withEnvelope(result));
    }

    private String riskStatus(Tenant tenant, int score) {
        if (tenant.getSubscriptionEndDate() != null && ChronoUnit.DAYS.between(LocalDate.now(), tenant.getSubscriptionEndDate()) <= 7) {
            return "SUBSCRIPTION_EXPIRES_SOON";
        }
        if (score < 20) return "USAGE_BELOW_20_PERCENT";
        return "HEALTHY";
    }

    private Map<String, Object> defaultHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", 20);
        result.put("label", "Getting Started");
        result.put("vehiclesAdded", 0);
        result.put("reservationsCreated", 0);
        result.put("contractsSigned", 0);
        result.put("gpsConnected", 0);
        result.put("employeeActivity", 0);
        result.put("risk", "GETTING_STARTED");
        result.put("completedSteps", 1);
        result.put("totalSteps", 5);
        result.put("steps", healthSteps(false, false, false, false));
        return result;
    }

    private java.util.List<Map<String, Object>> healthSteps(
            boolean vehicleAdded,
            boolean clientAdded,
            boolean reservationCreated,
            boolean contractCreated) {
        return java.util.List.of(
                step("AGENCY_CREATED", "Agency created", true),
                step("VEHICLE_ADDED", "Add your first vehicle", vehicleAdded),
                step("CLIENT_ADDED", "Add your first client", clientAdded),
                step("RESERVATION_CREATED", "Create your first reservation", reservationCreated),
                step("CONTRACT_CREATED", "Generate your first contract", contractCreated)
        );
    }

    private Map<String, Object> step(String key, String label, boolean completed) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("key", key);
        step.put("label", label);
        step.put("completed", completed);
        return step;
    }

    private Map<String, Object> withEnvelope(Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>(data);
        response.put("success", true);
        response.put("message", "SaaS health loaded successfully");
        response.put("data", data);
        return response;
    }
}
