package com.carrental.service;

import com.carrental.dto.maintenance.CreateMaintenanceRequest;
import com.carrental.dto.maintenance.MaintenanceResponse;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.Notification;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.User;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleMaintenance;
import com.carrental.entity.VehicleStatus;
import com.carrental.exception.MaintenanceValidationException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleMaintenanceRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleMaintenanceService {
    private final VehicleMaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> list(Long vehicleId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<VehicleMaintenance> rows = vehicleId == null
                ? maintenanceRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                : maintenanceRepository.findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(tenantId, vehicleId);

        long scheduled  = rows.stream().filter(r -> r.getStatus() == MaintenanceStatus.SCHEDULED).count();
        long inProgress = rows.stream().filter(r -> r.getStatus() == MaintenanceStatus.IN_PROGRESS).count();
        long completed  = rows.stream().filter(r -> r.getStatus() == MaintenanceStatus.COMPLETED).count();
        BigDecimal totalCost = rows.stream()
                .map(r -> r.getCost() != null ? r.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("[MAINTENANCE_LIST_DEBUG] tenantId={} vehicleId={} count={} scheduled={} inProgress={} completed={} totalCost={} firstId={}",
                tenantId, vehicleId, rows.size(), scheduled, inProgress, completed, totalCost,
                rows.isEmpty() ? null : rows.get(0).getId());

        return rows.stream().map(row -> toResponse(row, tenantId)).toList();
    }

    @Transactional
    public MaintenanceResponse create(CreateMaintenanceRequest request) {
        User user = currentUser();
        Long tenantId = TenantContext.getCurrentTenantId();

        log.info("[MAINTENANCE_CREATE_DEBUG] currentUserId={} currentUserRole={} agencyId={} vehicleId={} title={} "
                + "serviceProvider={} scheduledDateRaw={} scheduledDateParsed={} cost={} mileage={} descriptionPresent={} "
                + "notificationEnabled=true requestPayload={}",
                user != null ? user.getId() : null,
                user != null ? user.getRole() : null,
                tenantId,
                request != null ? request.getVehicleId() : null,
                request != null ? request.getTitle() : null,
                request != null ? request.getServiceProvider() : null,
                request != null ? request.getScheduledDate() : null,
                request != null ? request.getScheduledDate() : null,
                request != null ? request.getCost() : null,
                request != null ? request.getMileage() : null,
                request != null && request.getDescription() != null && !request.getDescription().isBlank(),
                request);

        if (user == null) {
            log.warn("[MAINTENANCE_CREATE_DEBUG] result=FAILED errorCode=AUTH_REQUIRED");
            throw new MaintenanceValidationException("Authentication is required to create a maintenance work order.", "AUTH_REQUIRED");
        }

        validateCreate(request);

        if (tenantId == null) {
            log.warn("[MAINTENANCE_CREATE_DEBUG] result=FAILED errorCode=AGENCY_CONTEXT_MISSING currentUserId={}",
                    user.getId());
            throw new MaintenanceValidationException("Current user is not linked to an agency.", "AGENCY_CONTEXT_MISSING");
        }

        Vehicle anyVehicle = vehicleRepository.findById(request.getVehicleId()).orElse(null);
        log.info("[MAINTENANCE_CREATE_DEBUG] vehicleExists={} payloadVehicleId={}", anyVehicle != null, request.getVehicleId());
        if (anyVehicle == null) {
            log.warn("[MAINTENANCE_CREATE_DEBUG] result=FAILED errorCode=VEHICLE_NOT_FOUND payloadVehicleId={} agencyId={}",
                    request.getVehicleId(), tenantId);
            throw new ResourceNotFoundException("Vehicle not found.");
        }
        Long vehicleAgencyId = anyVehicle.getTenant() != null ? anyVehicle.getTenant().getId() : null;
        if (vehicleAgencyId == null || !vehicleAgencyId.equals(tenantId)) {
            log.warn("[MAINTENANCE_CREATE_DEBUG] result=FAILED errorCode=VEHICLE_NOT_IN_AGENCY payloadVehicleId={} vehicleExists=true vehicleAgencyId={} agencyId={}",
                    request.getVehicleId(), vehicleAgencyId, tenantId);
            throw new org.springframework.security.access.AccessDeniedException("Vehicle does not belong to your agency.");
        }

        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(request.getVehicleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found."));

        MaintenanceStatus status = MaintenanceStatus.SCHEDULED;
        VehicleStatus vehicleStatusBefore = vehicle.getStatut();

        // A vehicle that is actively RENTED cannot be pulled into maintenance immediately.
        if (vehicleStatusBefore == VehicleStatus.RENTED) {
            log.warn("[MAINTENANCE_CREATE_DEBUG] result=FAILED errorCode=VEHICLE_CURRENTLY_RENTED vehicleId={} vehicleStatusBefore={}",
                    vehicle.getId(), vehicleStatusBefore);
            throw new MaintenanceValidationException(
                    "A rented vehicle cannot be put into maintenance while a rental contract is active. Complete or cancel the contract first.",
                    "VEHICLE_CURRENTLY_RENTED");
        }

        log.info("[MAINTENANCE_CREATE_DEBUG] vehicleExists=true vehicleAgencyId={} vehicleStatusBefore={} status={}",
                vehicleAgencyId, vehicleStatusBefore, status);

        VehicleMaintenance maintenance = VehicleMaintenance.builder()
                .tenant(vehicle.getTenant())
                .vehicle(vehicle)
                .title(request.getTitle().trim())
                .description(blankToNull(request.getDescription()))
                .serviceProvider(blankToNull(request.getServiceProvider()))
                .scheduledAt(request.getScheduledDate())
                .cost(request.getCost() == null ? BigDecimal.ZERO : request.getCost())
                .mileage(request.getMileage())
                .status(status)
                .createdBy(user)
                .build();

        // Always block the vehicle as soon as a work order is created.
        vehicle.setStatut(VehicleStatus.MAINTENANCE);
        vehicleRepository.saveAndFlush(vehicle);

        VehicleMaintenance saved = maintenanceRepository.saveAndFlush(maintenance);
        log.info("[MAINTENANCE_CREATE_DEBUG] workOrderSaved=true workOrderId={} vehicleId={} vehicleStatusBefore={} vehicleStatusAfter={} scheduledDateParsed={} result=SUCCESS",
                saved.getId(), vehicle.getId(), vehicleStatusBefore, vehicle.getStatut(), saved.getScheduledAt());

        Hibernate.initialize(saved.getVehicle());
        Hibernate.initialize(saved.getCreatedBy());

        List<String> warnings = notifySafely(
                "Maintenance scheduled",
                vehicle.getMarque() + " (" + vehicle.getPlate() + "): " + saved.getTitle(),
                Notification.NotificationType.MAINTENANCE_CREATED,
                Notification.Severity.WARNING,
                saved.getId(), tenantId);

        MaintenanceResponse response = toResponse(saved, tenantId);
        response.setWarnings(warnings);
        return response;
    }

    @Transactional
    public MaintenanceResponse updateStatus(Long id, MaintenanceStatus status) {
        if (status == null) {
            throw new MaintenanceValidationException("Maintenance status is required", "STATUS_REQUIRED");
        }

        User user = currentUser();
        Long tenantId = TenantContext.getCurrentTenantId();
        MaintenanceStatus currentStatusForLog = null;
        Long vehicleIdForLog = null;
        VehicleStatus vehicleStatusBeforeForLog = null;
        VehicleStatus vehicleStatusAfterForLog = null;
        Notification.NotificationType notificationTypeForLog = null;

        log.info("[MAINTENANCE_STATUS_DEBUG] userId={} email={} role={} agencyId={} workOrderId={} requestedStatus={}",
                user != null ? user.getId() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getRole() : null,
                tenantId, id, status);

        try {
            if (user == null) {
                log.warn("[MAINTENANCE_STATUS_DEBUG] workOrderId={} result=AUTH_REQUIRED", id);
                throw new MaintenanceValidationException("Authentication is required to update a maintenance work order.", "AUTH_REQUIRED");
            }

            if (tenantId == null) {
                log.warn("[MAINTENANCE_STATUS_DEBUG] workOrderId={} result=AGENCY_CONTEXT_MISSING", id);
                throw new MaintenanceValidationException("Current user is not linked to an agency.", "AGENCY_CONTEXT_MISSING");
            }

            // Step 1: load the work order
            VehicleMaintenance maintenance = maintenanceRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> {
                        log.warn("[MAINTENANCE_STATUS_DEBUG] workOrderId={} agencyId={} result=WORK_ORDER_NOT_FOUND", id, tenantId);
                        return new ResourceNotFoundException("Maintenance work order not found");
                    });

            MaintenanceStatus currentStatus = maintenance.getStatus();
            currentStatusForLog = currentStatus;
            log.info("[MAINTENANCE_STATUS_DEBUG] workOrderId={} workOrderExists=true workOrderAgencyId={} currentStatus={}",
                    id, tenantId, currentStatus);

            // Step 2: enforce the allowed status transition matrix
            validateTransition(id, currentStatus, status);

            // Step 3: resolve vehicle ID via direct query - avoids lazy-init through @SQLRestriction proxy
            Long vehicleId = maintenanceRepository.findVehicleIdById(id);
            vehicleIdForLog = vehicleId;
            if (vehicleId == null) {
                log.error("[MAINTENANCE_STATUS_DEBUG] workOrderId={} result=VEHICLE_ID_NULL", id);
                throw new ResourceNotFoundException("Vehicle not found for this maintenance record.");
            }
            log.info("[MAINTENANCE_STATUS_DEBUG] workOrderId={} vehicleId={}", id, vehicleId);

            // Step 4: load vehicle with pessimistic write lock
            Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(vehicleId, tenantId)
                    .orElseThrow(() -> {
                        log.warn("[MAINTENANCE_STATUS_DEBUG] workOrderId={} vehicleId={} agencyId={} vehicleExists=false result=VEHICLE_NOT_FOUND",
                                id, vehicleId, tenantId);
                        return new ResourceNotFoundException("Vehicle not found.");
                    });

            VehicleStatus vehicleStatusBefore = vehicle.getStatut();
            vehicleStatusBeforeForLog = vehicleStatusBefore;
            log.info("[MAINTENANCE_STATUS_DEBUG] workOrderId={} vehicleId={} vehicleExists=true vehicleStatusBefore={}",
                    id, vehicleId, vehicleStatusBefore);

            // Step 5: apply status transition
            maintenance.setStatus(status);
            if (status == MaintenanceStatus.IN_PROGRESS) {
                if (maintenance.getStartedAt() == null) maintenance.setStartedAt(LocalDateTime.now());
                vehicle.setStatut(VehicleStatus.MAINTENANCE);
            } else if (status == MaintenanceStatus.COMPLETED || status == MaintenanceStatus.CANCELLED) {
                if (status == MaintenanceStatus.COMPLETED) maintenance.setCompletedAt(LocalDateTime.now());
                int activeMaintenanceCount = countOtherOpenMaintenance(tenantId, vehicleId, maintenance.getId());
                boolean hasReservation     = hasActiveReservation(tenantId, vehicleId);
                boolean hasContract        = hasActiveContract(tenantId, vehicleId);
                log.info("[MAINTENANCE_STATUS_DEBUG] workOrderId={} vehicleId={} activeMaintenanceCount={} activeReservationCount={} activeContractCount={}",
                        id, vehicleId, activeMaintenanceCount, hasReservation ? 1 : 0, hasContract ? 1 : 0);
                if (activeMaintenanceCount == 0 && !hasReservation && !hasContract) {
                    vehicle.setStatut(VehicleStatus.AVAILABLE);
                }
            }

            VehicleStatus vehicleStatusAfter = vehicle.getStatut();
            vehicleStatusAfterForLog = vehicleStatusAfter;

            // Step 6: persist both entities
            vehicleRepository.save(vehicle);
            VehicleMaintenance saved = maintenanceRepository.save(maintenance);

            log.info("[MAINTENANCE_STATUS_DEBUG] workOrderId={} vehicleId={} vehicleStatusBefore={} vehicleStatusAfter={} newStatus={} result=SUCCESS",
                    saved.getId(), vehicleId, vehicleStatusBefore, vehicleStatusAfter, saved.getStatus());

            // Step 7: eagerly initialize lazy associations for toResponse()
            Hibernate.initialize(saved.getVehicle());
            Hibernate.initialize(saved.getCreatedBy());

            // Step 8: non-critical notification
            Notification.NotificationType nType = switch (status) {
                case IN_PROGRESS -> Notification.NotificationType.MAINTENANCE_STARTED;
                case COMPLETED   -> Notification.NotificationType.MAINTENANCE_COMPLETED;
                case CANCELLED   -> Notification.NotificationType.MAINTENANCE_CANCELLED;
                default          -> Notification.NotificationType.MAINTENANCE_STATUS_UPDATED;
            };
            notificationTypeForLog = nType;
            Notification.Severity nSeverity = switch (status) {
                case COMPLETED -> Notification.Severity.SUCCESS;
                case CANCELLED -> Notification.Severity.WARNING;
                default        -> Notification.Severity.INFO;
            };
            String nTitle = switch (status) {
                case IN_PROGRESS -> "Maintenance started";
                case COMPLETED   -> "Maintenance completed";
                case CANCELLED   -> "Maintenance cancelled";
                default          -> "Maintenance updated";
            };
            List<String> warnings = notifySafely(
                    nTitle,
                    vehicle.getMarque() + " (" + vehicle.getPlate() + "): " + saved.getTitle(),
                    nType, nSeverity, saved.getId(), tenantId);

            MaintenanceResponse response = toResponse(saved, tenantId);
            response.setWarnings(warnings);

            log.info("[MAINTENANCE_STATUS_UPDATE_DEBUG] currentUserId={} agencyId={} workOrderId={} oldStatus={} newStatus={} "
                    + "vehicleId={} vehicleStatusBefore={} vehicleStatusAfter={} notificationType={} result=SUCCESS",
                    user.getId(), tenantId, id, currentStatusForLog, status,
                    vehicleIdForLog, vehicleStatusBeforeForLog, vehicleStatusAfterForLog, notificationTypeForLog);
            return response;
        } catch (RuntimeException ex) {
            log.error("[MAINTENANCE_STATUS_UPDATE_DEBUG] currentUserId={} agencyId={} workOrderId={} oldStatus={} newStatus={} "
                    + "vehicleId={} vehicleStatusBefore={} vehicleStatusAfter={} notificationType={} result=FAILED exceptionClass={} exceptionMessage={}",
                    user != null ? user.getId() : null, tenantId, id, currentStatusForLog, status,
                    vehicleIdForLog, vehicleStatusBeforeForLog, vehicleStatusAfterForLog, notificationTypeForLog,
                    ex.getClass().getName(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Enforces the allowed maintenance status transition matrix:
     * SCHEDULED -> IN_PROGRESS | CANCELLED
     * IN_PROGRESS -> COMPLETED | CANCELLED
     * COMPLETED / CANCELLED -> (terminal, no further transitions)
     */
    private void validateTransition(Long workOrderId, MaintenanceStatus from, MaintenanceStatus to) {
        boolean allowed = switch (from) {
            case SCHEDULED -> to == MaintenanceStatus.IN_PROGRESS || to == MaintenanceStatus.CANCELLED;
            case IN_PROGRESS -> to == MaintenanceStatus.COMPLETED || to == MaintenanceStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            log.warn("[MAINTENANCE_STATUS_DEBUG] workOrderId={} currentStatus={} requestedStatus={} result=INVALID_MAINTENANCE_STATUS_TRANSITION",
                    workOrderId, from, to);
            throw new MaintenanceValidationException(
                    "Cannot change maintenance status from " + from + " to " + to + ".",
                    "INVALID_MAINTENANCE_STATUS_TRANSITION");
        }
    }

    /**
     * Finds vehicles that are marked MAINTENANCE but have no active (SCHEDULED or IN_PROGRESS)
     * work order, then auto-creates a repair work order for each. Returns a summary.
     */
    @Transactional
    public Map<String, Object> repairMissingWorkOrders() {
        User user = currentUser();
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new MaintenanceValidationException("Current user is not linked to an agency.", "AGENCY_CONTEXT_MISSING");
        }

        List<Vehicle> maintenanceVehicles = vehicleRepository.findAllByTenantIdAndStatut(tenantId, VehicleStatus.MAINTENANCE);

        List<Long> repairedIds = new ArrayList<>();
        for (Vehicle vehicle : maintenanceVehicles) {
            boolean hasOpen = maintenanceRepository.existsByTenantIdAndVehicleIdAndStatusIn(
                    tenantId, vehicle.getId(),
                    List.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS));
            if (!hasOpen) {
                VehicleMaintenance repair = VehicleMaintenance.builder()
                        .tenant(vehicle.getTenant())
                        .vehicle(vehicle)
                        .title("Maintenance status repair")
                        .description("Auto-created because vehicle was already marked as maintenance without a work order.")
                        .scheduledAt(LocalDateTime.now())
                        .cost(BigDecimal.ZERO)
                        .status(MaintenanceStatus.SCHEDULED)
                        .createdBy(user)
                        .build();
                maintenanceRepository.save(repair);
                repairedIds.add(vehicle.getId());
                log.info("[MAINTENANCE_REPAIR_DEBUG] auto-created work order vehicleId={} tenantId={}", vehicle.getId(), tenantId);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repaired", repairedIds.size());
        result.put("vehicleIds", repairedIds);
        result.put("message", repairedIds.isEmpty()
                ? "No vehicles required repair  -  all MAINTENANCE vehicles already have an active work order."
                : repairedIds.size() + " vehicle(s) repaired: auto-generated work orders created.");
        return result;
    }

    /**
     * Returns how many vehicles are currently in MAINTENANCE without an active work order.
     * Used by the frontend to decide whether to show the repair banner.
     */
    @Transactional(readOnly = true)
    public int countOrphanedMaintenanceVehicles() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return 0;
        List<Vehicle> maintenanceVehicles = vehicleRepository.findAllByTenantIdAndStatut(tenantId, VehicleStatus.MAINTENANCE);
        int count = 0;
        for (Vehicle v : maintenanceVehicles) {
            boolean hasOpen = maintenanceRepository.existsByTenantIdAndVehicleIdAndStatusIn(
                    tenantId, v.getId(), List.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS));
            if (!hasOpen) count++;
        }
        return count;
    }

    // â"€â"€ Mapping â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private MaintenanceResponse toResponse(VehicleMaintenance row, Long tenantId) {
        Vehicle vehicle = row.getVehicle();
        Long resolvedTenantId = tenantId != null ? tenantId : TenantContext.getCurrentTenantId();
        Long vehicleId = vehicle != null ? vehicle.getId() : null;
        String vehicleName = vehicle != null && vehicle.getMarque() != null && !vehicle.getMarque().isBlank()
                ? vehicle.getMarque()
                : "Unknown vehicle";
        String vehiclePlate = vehicle != null && vehicle.getPlate() != null ? vehicle.getPlate() : null;

        return MaintenanceResponse.builder()
                .id(row.getId())
                .agencyId(resolvedTenantId)
                .vehicleId(vehicleId)
                .vehicle(vehicleName)
                .plate(vehiclePlate)
                .title(row.getTitle())
                .description(row.getDescription() != null ? row.getDescription() : "")
                .serviceProvider(row.getServiceProvider() != null ? row.getServiceProvider() : "")
                .scheduledDate(row.getScheduledAt())
                .scheduledAt(row.getScheduledAt())
                .startedAt(row.getStartedAt())
                .completedDate(row.getCompletedAt())
                .completedAt(row.getCompletedAt())
                .cost(row.getCost() != null ? row.getCost() : BigDecimal.ZERO)
                .mileage(row.getMileage())
                .status(row.getStatus())
                .createdBy(row.getCreatedBy() != null ? row.getCreatedBy().getId() : null)
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    // Validation â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private void validateCreate(CreateMaintenanceRequest request) {
        if (request == null || request.getVehicleId() == null) {
            throw new MaintenanceValidationException("Vehicle is required", "VEHICLE_REQUIRED");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new MaintenanceValidationException("Title is required", "TITLE_REQUIRED");
        }
        if (request.getScheduledDate() == null) {
            throw new MaintenanceValidationException("Scheduled date is required", "INVALID_SCHEDULED_DATE");
        }
        if (request.getCost() != null && request.getCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new MaintenanceValidationException("Cost must be greater than or equal to 0", "INVALID_COST");
        }
        if (request.getMileage() != null && request.getMileage() < 0) {
            throw new MaintenanceValidationException("Mileage must be greater than or equal to 0", "INVALID_MILEAGE");
        }
    }

    // â"€â"€ Helpers â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private boolean hasOtherOpenMaintenance(Long tenantId, Long vehicleId, Long excludeId) {
        return countOtherOpenMaintenance(tenantId, vehicleId, excludeId) > 0;
    }

    private int countOtherOpenMaintenance(Long tenantId, Long vehicleId, Long excludeId) {
        return (int) maintenanceRepository.findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(tenantId, vehicleId).stream()
                .filter(row -> !row.getId().equals(excludeId)
                        && (row.getStatus() == MaintenanceStatus.SCHEDULED
                                || row.getStatus() == MaintenanceStatus.IN_PROGRESS))
                .count();
    }

    private boolean hasActiveReservation(Long tenantId, Long vehicleId) {
        return reservationRepository.existsByVehicleIdAndTenantIdAndStatusIn(vehicleId, tenantId,
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.ACTIVE));
    }

    private boolean hasActiveContract(Long tenantId, Long vehicleId) {
        return contractRepository.existsActiveVehicleContract(vehicleId, tenantId,
                List.of(ContractStatus.WAITING_SIGNATURE, ContractStatus.WAITING_CLIENT_SIGNATURE,
                        ContractStatus.PENDING_SIGNATURE, ContractStatus.PARTIALLY_SIGNED,
                        ContractStatus.SIGNED, ContractStatus.ACTIVE, ContractStatus.PAID));
    }

    private boolean hasActiveReservationOrContract(Long tenantId, Long vehicleId) {
        return hasActiveReservation(tenantId, vehicleId) || hasActiveContract(tenantId, vehicleId);
    }

    /**
     * Creates a maintenance notification without ever letting the failure
     * propagate into the calling (already-committed) work order/status
     * transaction. NotificationService runs this in its own REQUIRES_NEW
     * transaction, so even a DB-level rejection (e.g. an out-of-sync check
     * constraint) only fails that isolated insert, not the maintenance write.
     */
    private List<String> notifySafely(String title, String message,
            Notification.NotificationType type, Notification.Severity severity,
            Long workOrderId, Long tenantId) {
        try {
            notificationService.notifyMaintenance(title, message, type, severity, workOrderId, tenantId);
            log.info("[MAINTENANCE_NOTIFICATION_DEBUG] workOrderId={} notificationType={} notificationResult=SUCCESS",
                    workOrderId, type);
            return List.of();
        } catch (Exception ex) {
            log.warn("[MAINTENANCE_NOTIFICATION_DEBUG] workOrderId={} notificationType={} notificationResult=FAILED errorCode={} message={}",
                    workOrderId, type, ex.getClass().getSimpleName(), ex.getMessage());
            return List.of("NOTIFICATION_CREATE_FAILED");
        }
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal() : null;
        return principal instanceof User u ? u : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}


