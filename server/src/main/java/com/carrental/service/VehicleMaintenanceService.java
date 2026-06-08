package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.VehicleMaintenanceRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VehicleMaintenanceService {
    private final VehicleMaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long vehicleId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<VehicleMaintenance> rows = vehicleId == null
                ? maintenanceRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                : maintenanceRepository.findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(tenantId, vehicleId);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Long vehicleId = Long.valueOf(required(body, "vehicleId"));
        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        if (vehicle.getStatut() == VehicleStatus.RENTED) {
            throw new IllegalStateException("A rented vehicle cannot enter maintenance before its contract is completed.");
        }
        VehicleMaintenance maintenance = maintenanceRepository.save(VehicleMaintenance.builder()
                .tenant(vehicle.getTenant())
                .vehicle(vehicle)
                .title(required(body, "title"))
                .description(value(body, "description"))
                .serviceProvider(value(body, "serviceProvider"))
                .scheduledAt(dateTime(body.get("scheduledAt")))
                .cost(decimal(body.get("cost")))
                .mileage(integer(body.get("mileage")))
                .status(body.get("status") != null
                        ? MaintenanceStatus.valueOf(body.get("status").toString()) : MaintenanceStatus.SCHEDULED)
                .build());
        if (maintenance.getStatus() == MaintenanceStatus.IN_PROGRESS) {
            vehicle.setStatut(VehicleStatus.IN_MAINTENANCE);
            maintenance.setStartedAt(LocalDateTime.now());
            vehicleRepository.save(vehicle);
        }
        notificationService.createNotification("Vehicle maintenance created",
                vehicle.getMarque() + ": " + maintenance.getTitle(),
                Notification.NotificationType.INFORMATION, null, tenantId);
        return toResponse(maintenance);
    }

    @Transactional
    public Map<String, Object> updateStatus(Long id, MaintenanceStatus status) {
        Long tenantId = TenantContext.getCurrentTenantId();
        VehicleMaintenance maintenance = maintenanceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance work order not found"));
        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(maintenance.getVehicle().getId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        maintenance.setStatus(status);
        if (status == MaintenanceStatus.IN_PROGRESS) {
            maintenance.setStartedAt(LocalDateTime.now());
            vehicle.setStatut(VehicleStatus.IN_MAINTENANCE);
        } else if (status == MaintenanceStatus.COMPLETED || status == MaintenanceStatus.CANCELLED) {
            if (status == MaintenanceStatus.COMPLETED) maintenance.setCompletedAt(LocalDateTime.now());
            boolean otherOpen = maintenanceRepository.existsByTenantIdAndVehicleIdAndStatusIn(
                    tenantId, vehicle.getId(), List.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS));
            if (!otherOpen) vehicle.setStatut(VehicleStatus.AVAILABLE);
        }
        vehicleRepository.save(vehicle);
        VehicleMaintenance saved = maintenanceRepository.save(maintenance);
        notificationService.createNotification("Vehicle maintenance " + status.name().toLowerCase(),
                vehicle.getMarque() + ": " + saved.getTitle(),
                status == MaintenanceStatus.COMPLETED
                        ? Notification.NotificationType.SUCCESS : Notification.NotificationType.INFORMATION,
                null, tenantId);
        return toResponse(saved);
    }

    private Map<String, Object> toResponse(VehicleMaintenance row) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", row.getId());
        response.put("vehicleId", row.getVehicle().getId());
        response.put("vehicle", row.getVehicle().getMarque());
        response.put("plate", row.getVehicle().getPlate() != null ? row.getVehicle().getPlate() : "");
        response.put("title", row.getTitle());
        response.put("description", row.getDescription() != null ? row.getDescription() : "");
        response.put("serviceProvider", row.getServiceProvider() != null ? row.getServiceProvider() : "");
        response.put("scheduledAt", row.getScheduledAt());
        response.put("startedAt", row.getStartedAt());
        response.put("completedAt", row.getCompletedAt());
        response.put("cost", row.getCost() != null ? row.getCost() : BigDecimal.ZERO);
        response.put("mileage", row.getMileage());
        response.put("status", row.getStatus());
        response.put("createdAt", row.getCreatedAt());
        return response;
    }

    private String required(Map<String, Object> body, String key) {
        String value = value(body, key);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private String value(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private LocalDateTime dateTime(Object value) {
        return value == null || value.toString().isBlank() ? null : LocalDateTime.parse(value.toString());
    }

    private BigDecimal decimal(Object value) {
        return value == null || value.toString().isBlank() ? null : new BigDecimal(value.toString());
    }

    private Integer integer(Object value) {
        return value == null || value.toString().isBlank() ? null : Integer.valueOf(value.toString());
    }
}
