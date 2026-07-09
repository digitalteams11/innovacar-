package com.carrental.service;

import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.Tenant;
import com.carrental.exception.PlanLimitException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enforces subscription plan limits for all quota-tracked resources.
 * Called before create operations; throws {@link PlanLimitException} (→ 403)
 * when the tenant has exhausted their plan quota.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanLimitService {

    private final TenantRepository       tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final VehicleRepository      vehicleRepository;
    private final EmployeeRepository     employeeRepository;
    private final ClientRepository       clientRepository;
    private final ReservationRepository  reservationRepository;
    private final ContractRepository     contractRepository;
    // ── Vehicle limit ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void assertVehicleLimit() {
        check("VEHICLES", "vehicles");
    }

    // ── Employee limit ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void assertEmployeeLimit() {
        check("EMPLOYEES", "employees");
    }

    // ── Client limit ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void assertClientLimit() {
        check("CLIENTS", "clients");
    }

    // ── Reservation limit ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void assertReservationLimit() {
        check("RESERVATIONS", "reservations");
    }

    // ── Contract limit ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void assertContractLimit() {
        check("CONTRACTS", "contracts");
    }

    // ── GPS device limit ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void assertGpsDeviceLimit() {
        check("GPS_DEVICES", "gpsDevices");
    }

    // ── Combined usage summary for API responses ──────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getUsageSummary() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return Map.of();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return Map.of();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vehiclesUsed",      vehicleRepository.countByTenantId(tenantId));
        map.put("vehiclesLimit",     resolveLimit(tenant, "vehicles"));
        map.put("employeesUsed",     employeeRepository.findAllByTenantIdAndDeletedFalse(tenantId).size());
        map.put("employeesLimit",    resolveLimit(tenant, "employees"));
        map.put("clientsUsed",       clientRepository.countByTenantId(tenantId));
        map.put("clientsLimit",      resolveLimit(tenant, "clients"));
        map.put("reservationsUsed",  reservationRepository.countByTenantId(tenantId));
        map.put("reservationsLimit", resolveLimit(tenant, "reservations"));
        map.put("contractsUsed",     contractRepository.countByTenantId(tenantId));
        map.put("contractsLimit",    resolveLimit(tenant, "contracts"));
        map.put("gpsDevicesUsed",    vehicleRepository.countByTenantIdAndGpsEnabledTrue(tenantId));
        map.put("gpsDevicesLimit",   resolveLimit(tenant, "gpsDevices"));
        return map;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void check(String resourceLabel, String key) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return;
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return;

        long used  = countUsed(tenantId, key);
        int  limit = resolveLimit(tenant, key);
        if (limit > 0 && used >= limit) {
            log.warn("[PLAN_LIMIT] tenantId={} {} used={} limit={}", tenantId, resourceLabel, used, limit);
            throwLimitError(resourceLabel, used, limit, tenant);
        }
    }

    private long countUsed(Long tenantId, String key) {
        return switch (key) {
            case "vehicles"     -> vehicleRepository.countByTenantId(tenantId);
            case "employees"    -> employeeRepository.findAllByTenantIdAndDeletedFalse(tenantId).size();
            case "clients"      -> clientRepository.countByTenantId(tenantId);
            case "reservations" -> reservationRepository.countByTenantId(tenantId);
            case "contracts"    -> contractRepository.countByTenantId(tenantId);
            case "gpsDevices"   -> vehicleRepository.countByTenantIdAndGpsEnabledTrue(tenantId);
            default             -> 0L;
        };
    }

    int resolveLimit(Tenant tenant, String key) {
        SubscriptionPlan plan = tenant.getPlanName() == null ? null
                : planRepository.findByName(tenant.getPlanName())
                        .or(() -> planRepository.findByCode(tenant.getPlanName()))
                        .orElse(null);
        return switch (key) {
            case "vehicles" -> plan != null && plan.getMaxVehicles() != null
                    ? plan.getMaxVehicles()
                    : (tenant.getMaxVehicles() != null ? tenant.getMaxVehicles() : 0);
            case "employees" -> plan != null && plan.getMaxEmployees() != null
                    ? plan.getMaxEmployees()
                    : (tenant.getMaxEmployees() != null ? tenant.getMaxEmployees() : 0);
            case "clients" -> plan != null && plan.getClientLimit() != null
                    ? plan.getClientLimit()
                    : 0;
            case "reservations" -> plan != null && plan.getMaxReservations() != null
                    ? plan.getMaxReservations()
                    : 0;
            case "contracts" -> plan != null && plan.getContractLimit() != null
                    ? plan.getContractLimit()
                    : 0;
            case "gpsDevices" -> plan != null && plan.getMaxGpsDevices() != null
                    ? plan.getMaxGpsDevices()
                    : (tenant.getMaxGpsDevices() != null ? tenant.getMaxGpsDevices() : 0);
            default -> 0;
        };
    }

    private void throwLimitError(String resource, long used, long limit, Tenant tenant) {
        String currentPlan = tenant.getPlanName() != null ? tenant.getPlanName() : "Unknown";
        List<String> upgradePlans = planRepository
                .findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(p -> {
                    Integer planLimit = switch (resource) {
                        case "VEHICLES"     -> p.getMaxVehicles();
                        case "EMPLOYEES"    -> p.getMaxEmployees();
                        case "CLIENTS"      -> p.getClientLimit();
                        case "RESERVATIONS" -> p.getMaxReservations();
                        case "CONTRACTS"    -> p.getContractLimit();
                        case "GPS_DEVICES"  -> p.getMaxGpsDevices();
                        default             -> null;
                    };
                    return planLimit != null && planLimit > limit;
                })
                .map(SubscriptionPlan::getCode)
                .toList();
        throw new PlanLimitException(resource, used, limit, upgradePlans, currentPlan);
    }
}


