package com.carrental.service;

import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.VehicleMaintenance;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.VehicleMaintenanceRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Smart vehicle availability engine with conflict detection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final VehicleMaintenanceRepository maintenanceRepository;

    /**
     * Get all available vehicles for a date range.
     * Excludes: rented, maintenance, blocked vehicles and vehicles with overlapping reservations.
     */
    @Transactional(readOnly = true)
    public List<Vehicle> getAvailableVehicles(
            LocalDate startDate, LocalTime startTime,
            LocalDate endDate, LocalTime endTime,
            Long excludeReservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        validateRange(startDate, startTime, endDate, endTime);
        // Mirror isVehicleAvailable's status rule: RESERVED/RENTED only reflects
        // *some* active booking, not that the vehicle is busy for *this* date
        // range — vehicle.statut is never auto-reset to AVAILABLE once a rental
        // period ends, so filtering candidates on statut==AVAILABLE here would
        // permanently hide a vehicle from the selector after its first booking,
        // even for dates long after it was returned. Only hard, date-independent
        // statuses should be excluded up front; everything else is decided by
        // the actual reservation/contract/maintenance overlap checks below.
        List<Vehicle> candidates = vehicleRepository.findAllByTenantId(tenantId).stream()
                .filter(vehicle -> !EnumSet.of(VehicleStatus.OUT_OF_SERVICE, VehicleStatus.SOLD, VehicleStatus.ARCHIVED)
                        .contains(vehicle.getStatut()))
                .toList();

        // Get all active reservations in the date range
        List<Reservation> overlappingReservations = reservationRepository
                .findOverlappingReservations(tenantId, startDate, startTime, endDate, endTime, excludeReservationId);

        // Filter out vehicles with overlapping reservations, active contracts, or open maintenance.
        List<Long> reservedVehicleIds = overlappingReservations.stream()
                .map(r -> r.getVehicle().getId())
                .distinct()
                .toList();
        List<Long> contractVehicleIds = activeContractsInRange(
                tenantId, startDate, startTime, endDate, endTime).stream()
                .filter(contract -> contract.getVehicle() != null)
                .map(contract -> contract.getVehicle().getId())
                .distinct()
                .toList();
        List<Long> maintenanceVehicleIds = maintenanceRepository
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(row -> maintenanceBlocksRange(row, startDate, startTime, endDate, endTime))
                .map(row -> row.getVehicle().getId())
                .distinct()
                .toList();

        return candidates.stream()
                .filter(v -> !reservedVehicleIds.contains(v.getId()))
                .filter(v -> !contractVehicleIds.contains(v.getId()))
                .filter(v -> !maintenanceVehicleIds.contains(v.getId()))
                .toList();
    }

    /**
     * Check if a specific vehicle is available for a date range.
     */
    @Transactional(readOnly = true)
    public boolean isVehicleAvailable(
            Long vehicleId,
            LocalDate startDate, LocalTime startTime,
            LocalDate endDate, LocalTime endTime,
            Long excludeReservationId) {
        return isVehicleAvailable(vehicleId, startDate, startTime, endDate, endTime, excludeReservationId, null);
    }

    /**
     * Check if a specific vehicle is available for a date range, excluding a specific
     * contract from conflict detection (used during restore to avoid self-blocking).
     */
    @Transactional(readOnly = true)
    public boolean isVehicleAvailable(
            Long vehicleId,
            LocalDate startDate, LocalTime startTime,
            LocalDate endDate, LocalTime endTime,
            Long excludeReservationId,
            Long excludeContractId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        validateRange(startDate, startTime, endDate, endTime);
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId).orElse(null);
        if (vehicle == null) {
            return false;
        }
        // A vehicle flagged RESERVED/RENTED only means it has *some* active
        // booking — not that it's busy for *this* date range. Only statuses
        // that make the vehicle permanently unbookable (regardless of dates)
        // should short-circuit here; everything else is decided below by the
        // actual reservation/contract/maintenance date-overlap checks.
        if (EnumSet.of(VehicleStatus.OUT_OF_SERVICE, VehicleStatus.SOLD, VehicleStatus.ARCHIVED)
                .contains(vehicle.getStatut())) {
            return false;
        }

        List<Reservation> overlaps = reservationRepository
                .findOverlappingReservationsForVehicle(
                        tenantId, vehicleId, startDate, startTime, endDate, endTime, excludeReservationId);

        boolean contractOverlap = activeContractsInRange(tenantId, startDate, startTime, endDate, endTime, excludeContractId).stream()
                .anyMatch(contract -> contract.getVehicle() != null
                        && vehicleId.equals(contract.getVehicle().getId()));
        boolean openMaintenance = maintenanceRepository
                .findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(tenantId, vehicleId).stream()
                .anyMatch(row -> maintenanceBlocksRange(row, startDate, startTime, endDate, endTime));

        return overlaps.isEmpty() && !contractOverlap && !openMaintenance;
    }

    /**
     * Diagnostic lookup used only for logging: identifies exactly which
     * reservation/contract is blocking a vehicle for a date range, so a 409
     * conflict can be traced to a specific record instead of just "unavailable".
     */
    @Transactional(readOnly = true)
    public ConflictDetail findConflictDetail(
            Long vehicleId,
            LocalDate startDate, LocalTime startTime,
            LocalDate endDate, LocalTime endTime,
            Long excludeReservationId) {
        return findConflictDetail(vehicleId, startDate, startTime, endDate, endTime, excludeReservationId, null);
    }

    /**
     * Same as {@link #findConflictDetail} but also excludes a specific contract
     * from conflict detection — used during restore to avoid self-reporting.
     */
    @Transactional(readOnly = true)
    public ConflictDetail findConflictDetail(
            Long vehicleId,
            LocalDate startDate, LocalTime startTime,
            LocalDate endDate, LocalTime endTime,
            Long excludeReservationId,
            Long excludeContractId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        Reservation overlappingReservation = reservationRepository
                .findOverlappingReservationsForVehicle(
                        tenantId, vehicleId, startDate, startTime, endDate, endTime, excludeReservationId)
                .stream().findFirst().orElse(null);

        Contract overlappingContract = activeContractsInRange(tenantId, startDate, startTime, endDate, endTime, excludeContractId).stream()
                .filter(contract -> contract.getVehicle() != null && vehicleId.equals(contract.getVehicle().getId()))
                .findFirst().orElse(null);

        VehicleMaintenance blockingMaintenance = maintenanceRepository
                .findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(tenantId, vehicleId).stream()
                .filter(row -> maintenanceBlocksRange(row, startDate, startTime, endDate, endTime))
                .findFirst().orElse(null);

        // Reservation takes precedence as the reported conflict source when a
        // booking overlaps both as a reservation and (already converted) as a
        // contract — they describe the same underlying rental either way.
        String conflictSource = overlappingReservation != null ? "RESERVATION"
                : overlappingContract != null ? "CONTRACT"
                : blockingMaintenance != null ? "MAINTENANCE"
                : null;
        Long conflictId = overlappingReservation != null ? overlappingReservation.getId()
                : overlappingContract != null ? overlappingContract.getId()
                : blockingMaintenance != null ? blockingMaintenance.getId()
                : null;
        LocalDate conflictStartDate = overlappingReservation != null ? overlappingReservation.getDateStart()
                : overlappingContract != null ? overlappingContract.getStartDate()
                : blockingMaintenance != null ? blockingMaintenance.getScheduledAt().toLocalDate()
                : null;
        LocalDate conflictEndDate = overlappingReservation != null ? overlappingReservation.getDateEnd()
                : overlappingContract != null ? overlappingContract.getEndDate()
                : null;
        String conflictNumber = overlappingReservation != null ? "RES-" + overlappingReservation.getId()
                : overlappingContract != null ? overlappingContract.getContractNumber()
                : null;
        String conflictStatus = overlappingReservation != null ? overlappingReservation.getStatus().name()
                : overlappingContract != null ? overlappingContract.getStatus().name()
                : blockingMaintenance != null ? blockingMaintenance.getStatus().name()
                : null;

        return new ConflictDetail(
                overlappingReservation != null ? overlappingReservation.getId() : null,
                overlappingContract != null ? overlappingContract.getId() : null,
                blockingMaintenance != null ? blockingMaintenance.getId() : null,
                conflictSource, conflictId, conflictStartDate, conflictEndDate,
                conflictNumber, conflictStatus);
    }

    public record ConflictDetail(
            Long overlappingReservationId,
            Long overlappingContractId,
            Long blockingMaintenanceId,
            String conflictSource,
            Long conflictId,
            LocalDate conflictStartDate,
            LocalDate conflictEndDate,
            String conflictNumber,
            String conflictStatus) {}

    /**
     * Check for conflicting reservations/contract dates.
     */
    @Transactional(readOnly = true)
    public List<Reservation> findConflicts(
            Long tenantId, LocalDate startDate, LocalTime startTime,
            LocalDate endDate, LocalTime endTime, Long excludeId) {
        return reservationRepository.findOverlappingReservations(
                tenantId, startDate, startTime, endDate, endTime, excludeId);
    }

    /**
     * Get availability calendar for a vehicle (which days are booked).
     */
    @Transactional(readOnly = true)
    public List<LocalDate> getBookedDates(Long vehicleId, LocalDate from, LocalDate to) {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Reservation> reservations = reservationRepository
                .findByVehicleIdAndTenantIdAndStatusNot(vehicleId, tenantId, ReservationStatus.CANCELLED);

        return reservations.stream()
                .filter(r -> !r.getDateEnd().isBefore(from) && !r.getDateStart().isAfter(to))
                .flatMap(r -> r.getDateStart().datesUntil(r.getDateEnd().plusDays(1)))
                .distinct()
                .toList();
    }

    private void validateRange(LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime) {
        if (startDate.isAfter(endDate)
                || (startDate.equals(endDate) && !startTime.isBefore(endTime))) {
            throw new IllegalArgumentException("Rental end must be after rental start.");
        }
    }

    private List<Contract> activeContractsInRange(
            Long tenantId, LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime) {
        return activeContractsInRange(tenantId, startDate, startTime, endDate, endTime, null);
    }

    private List<Contract> activeContractsInRange(
            Long tenantId, LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime,
            Long excludeContractId) {
        LocalDateTime requestedStart = LocalDateTime.of(startDate, startTime);
        LocalDateTime requestedEnd = LocalDateTime.of(endDate, endTime);
        return vehicleRepository.findAllByTenantId(tenantId).stream()
                .flatMap(vehicle -> contractRepository.findConflictingContracts(
                        tenantId, vehicle.getId(), startDate, endDate, excludeContractId).stream())
                .filter(contract -> contract.getVehicle() != null)
                .filter(contract -> contract.getStartDate() != null && contract.getEndDate() != null)
                .filter(contract -> {
                    LocalTime contractStartTime = contract.getPickupTime() != null
                            ? contract.getPickupTime() : LocalTime.of(9, 0);
                    LocalTime contractEndTime = contract.getReturnTime() != null
                            ? contract.getReturnTime() : LocalTime.of(18, 0);
                    LocalDateTime contractStart = LocalDateTime.of(contract.getStartDate(), contractStartTime);
                    LocalDateTime contractEnd = LocalDateTime.of(contract.getEndDate(), contractEndTime);
                    return contractStart.isBefore(requestedEnd) && contractEnd.isAfter(requestedStart);
                })
                .toList();
    }

    private boolean maintenanceBlocksRange(
            VehicleMaintenance maintenance,
            LocalDate startDate,
            LocalTime startTime,
            LocalDate endDate,
            LocalTime endTime) {
        if (maintenance.getStatus() == MaintenanceStatus.IN_PROGRESS) {
            return true;
        }
        if (maintenance.getStatus() != MaintenanceStatus.SCHEDULED || maintenance.getScheduledAt() == null) {
            return false;
        }
        LocalDateTime requestedStart = LocalDateTime.of(startDate, startTime);
        LocalDateTime requestedEnd = LocalDateTime.of(endDate, endTime);
        LocalDateTime scheduledAt = maintenance.getScheduledAt();
        return !scheduledAt.isBefore(requestedStart) && scheduledAt.isBefore(requestedEnd);
    }
}
