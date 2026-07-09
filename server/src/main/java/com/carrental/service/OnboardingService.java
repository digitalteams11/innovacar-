package com.carrental.service;

import com.carrental.entity.OnboardingProgress;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final OnboardingProgressRepository progressRepository;
    private final TenantRepository tenantRepository;
    private final VehicleRepository vehicleRepository;
    private final ClientRepository clientRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final GpsSettingsRepository gpsSettingsRepository;

    @Transactional
    public Map<String, Object> status() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return envelope(defaultStatus());
        }
        OnboardingProgress progress = getOrCreate(tenantId);
        if (progress == null) {
            return envelope(defaultStatus());
        }
        Tenant tenant = progress.getTenant();

        long vehicles = vehicleRepository.countByTenantId(tenantId);
        long clients = clientRepository.countByTenantId(tenantId);
        long reservations = reservationRepository.countByTenantId(tenantId);
        long contracts = contractRepository.countByTenantId(tenantId);
        boolean gpsConfigured = gpsSettingsRepository.existsByTenantId(tenantId);
        boolean agencyConfigured = hasText(tenant.getName()) && hasText(tenant.getPhone())
                && hasText(tenant.getAddress()) && hasText(tenant.getCity()) && hasText(tenant.getCountry());

        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("agency", agencyConfigured);
        steps.put("business", true);
        steps.put("vehicle", vehicles > 0);
        steps.put("client", clients > 0);
        steps.put("reservation", reservations > 0);
        steps.put("contract", contracts > 0);
        steps.put("gps", gpsConfigured);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("welcomeDismissed", progress.isWelcomeDismissed());
        result.put("wizardSkipped", progress.isWizardSkipped());
        result.put("completed", progress.isCompleted());
        result.put("tourCompleted", progress.isTourCompleted());
        result.put("steps", steps);
        result.put("counts", Map.of(
                "vehicles", vehicles,
                "clients", clients,
                "reservations", reservations,
                "contracts", contracts
        ));
        result.put("currentStep", currentStep(steps));
        result.put("progress", progressPercent(steps));
        return envelope(result);
    }

    @Transactional
    public Map<String, Object> update(Map<String, Boolean> updates) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return envelope(defaultStatus());
        }
        OnboardingProgress progress = getOrCreate(tenantId);
        if (progress == null) {
            return envelope(defaultStatus());
        }
        if (updates.containsKey("welcomeDismissed")) progress.setWelcomeDismissed(updates.get("welcomeDismissed"));
        if (updates.containsKey("wizardSkipped")) progress.setWizardSkipped(updates.get("wizardSkipped"));
        if (updates.containsKey("completed")) progress.setCompleted(updates.get("completed"));
        if (updates.containsKey("tourCompleted")) progress.setTourCompleted(updates.get("tourCompleted"));
        progressRepository.save(progress);
        return status();
    }

    private OnboardingProgress getOrCreate(Long tenantId) {
        return progressRepository.findByTenantId(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant == null) return null;
            return progressRepository.save(OnboardingProgress.builder().tenant(tenant).build());
        });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> defaultStatus() {
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("agency", false);
        steps.put("business", false);
        steps.put("vehicle", false);
        steps.put("client", false);
        steps.put("reservation", false);
        steps.put("contract", false);
        steps.put("gps", false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("welcomeDismissed", false);
        result.put("wizardSkipped", false);
        result.put("completed", false);
        result.put("tourCompleted", false);
        result.put("steps", steps);
        result.put("counts", Map.of(
                "vehicles", 0,
                "clients", 0,
                "reservations", 0,
                "contracts", 0
        ));
        result.put("currentStep", "ADD_VEHICLE");
        result.put("progress", 20);
        return result;
    }

    private String currentStep(Map<String, Object> steps) {
        if (!Boolean.TRUE.equals(steps.get("vehicle"))) return "ADD_VEHICLE";
        if (!Boolean.TRUE.equals(steps.get("client"))) return "ADD_CLIENT";
        if (!Boolean.TRUE.equals(steps.get("reservation"))) return "CREATE_RESERVATION";
        if (!Boolean.TRUE.equals(steps.get("contract"))) return "CREATE_CONTRACT";
        return "COMPLETED";
    }

    private int progressPercent(Map<String, Object> steps) {
        int progress = 20;
        if (Boolean.TRUE.equals(steps.get("vehicle"))) progress += 20;
        if (Boolean.TRUE.equals(steps.get("client"))) progress += 20;
        if (Boolean.TRUE.equals(steps.get("reservation"))) progress += 20;
        if (Boolean.TRUE.equals(steps.get("contract"))) progress += 20;
        return Math.min(100, progress);
    }

    private Map<String, Object> envelope(Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>(data);
        Map<String, Object> standardData = new LinkedHashMap<>();
        standardData.put("welcomeDismissed", data.getOrDefault("welcomeDismissed", false));
        standardData.put("wizardSkipped", data.getOrDefault("wizardSkipped", false));
        standardData.put("completed", data.getOrDefault("completed", false));
        standardData.put("tourCompleted", data.getOrDefault("tourCompleted", false));
        standardData.put("currentStep", data.getOrDefault("currentStep", "ADD_VEHICLE"));
        standardData.put("progress", data.getOrDefault("progress", 20));
        Object rawSteps = data.get("steps");
        Map<?, ?> steps = rawSteps instanceof Map<?, ?> map ? map : Map.of();
        standardData.put("steps", List.of(
                step("AGENCY_CREATED", "Agency created", true),
                step("VEHICLE_ADDED", "Add your first vehicle", Boolean.TRUE.equals(steps.get("vehicle"))),
                step("CLIENT_ADDED", "Add your first client", Boolean.TRUE.equals(steps.get("client"))),
                step("RESERVATION_CREATED", "Create your first reservation", Boolean.TRUE.equals(steps.get("reservation"))),
                step("CONTRACT_CREATED", "Generate your first contract", Boolean.TRUE.equals(steps.get("contract")))
        ));
        response.put("success", true);
        response.put("message", "Onboarding loaded successfully");
        response.put("data", standardData);
        return response;
    }

    private Map<String, Object> step(String key, String label, boolean completed) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("key", key);
        value.put("label", label);
        value.put("completed", completed);
        return value;
    }
}
