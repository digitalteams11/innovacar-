package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleMaintenanceRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Single source of truth for a vehicle's lifecycle status.
 *
 * <p>Vehicle status is never trusted blindly — it is recomputed from the real
 * blockers (active maintenance, active contract, active reservation) any time
 * this service is asked to look at a vehicle. This fixes the class of bug
 * where a vehicle stays RESERVED forever because nothing ever re-evaluated it
 * after its reservation's end date/time passed.
 *
 * <p>Priority order (highest wins): ARCHIVED/SOLD/OUT_OF_SERVICE (manual/terminal,
 * never overridden here) &gt; MAINTENANCE &gt; RENTED (active contract) &gt;
 * RESERVED (contract awaiting signature, or a non-expired reservation) &gt; AVAILABLE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleStatusSyncService {

    private static final Set<VehicleStatus> TERMINAL_STATUSES =
            EnumSet.of(VehicleStatus.ARCHIVED, VehicleStatus.SOLD, VehicleStatus.OUT_OF_SERVICE);

    private static final List<MaintenanceStatus> ACTIVE_MAINTENANCE_STATUSES =
            List.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS);

    private static final List<ContractStatus> RENTED_CONTRACT_STATUSES =
            List.of(ContractStatus.ACTIVE, ContractStatus.PAID);

    private static final List<ContractStatus> RESERVED_CONTRACT_STATUSES =
            List.of(ContractStatus.WAITING_SIGNATURE, ContractStatus.WAITING_CLIENT_SIGNATURE,
                    ContractStatus.PENDING_SIGNATURE, ContractStatus.PARTIALLY_SIGNED, ContractStatus.SIGNED);

    private static final List<ContractStatus> ALL_ACTIVE_CONTRACT_STATUSES =
            List.of(ContractStatus.WAITING_SIGNATURE, ContractStatus.WAITING_CLIENT_SIGNATURE,
                    ContractStatus.PENDING_SIGNATURE, ContractStatus.PARTIALLY_SIGNED, ContractStatus.SIGNED,
                    ContractStatus.ACTIVE, ContractStatus.PAID);

    /** A confirmed/pending reservation blocks the vehicle until its endDateTime passes. */
    private static final List<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final VehicleMaintenanceRepository maintenanceRepository;

    // ── Public API ───────────────────────────────────────────────────────────

    /** Recalculates a single vehicle's status for the caller's current tenant. */
    @Transactional
    public VehicleStatus recalculateVehicleStatus(Long vehicleId) {
        return recalculateVehicleStatus(vehicleId, TenantContext.getCurrentTenantId());
    }

    /** Recalculates a single vehicle's status for an explicit tenant (used by scheduler/other services). */
    @Transactional
    public VehicleStatus recalculateVehicleStatus(Long vehicleId, Long tenantId) {
        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(vehicleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        return computeAndApply(vehicle, tenantId, LocalDateTime.now());
    }

    /**
     * Expires stale reservations for an agency: any PENDING/CONFIRMED reservation
     * whose end (date + time) has already passed is flipped to COMPLETED (if it
     * produced a completed contract) or EXPIRED otherwise, then the affected
     * vehicle's status is recalculated. Returns the number of reservations expired.
     */
    @Transactional
    public int expireOldReservations(Long agencyId) {
        if (agencyId == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expired = reservationRepository.findExpiredBlockingReservations(
                agencyId, BLOCKING_RESERVATION_STATUSES, now.toLocalDate(), now.toLocalTime());

        for (Reservation reservation : expired) {
            Contract contract = reservation.getContract();
            ReservationStatus newStatus = contract != null && contract.getStatus() == ContractStatus.COMPLETED
                    ? ReservationStatus.COMPLETED
                    : ReservationStatus.EXPIRED;
            reservation.setStatus(newStatus);
            reservationRepository.save(reservation);
            log.info("[VEHICLE_STATUS_SYNC] reservationId={} vehicleId={} agencyId={} action=RESERVATION_EXPIRED newStatus={}",
                    reservation.getId(),
                    reservation.getVehicle() != null ? reservation.getVehicle().getId() : null,
                    agencyId, newStatus);
        }

        expired.stream()
                .map(Reservation::getVehicle)
                .filter(Objects::nonNull)
                .map(Vehicle::getId)
                .distinct()
                .forEach(vehicleId -> vehicleRepository.findByIdAndTenantIdForUpdate(vehicleId, agencyId)
                        .ifPresent(vehicle -> computeAndApply(vehicle, agencyId, now)));

        return expired.size();
    }

    /**
     * Lightweight sync: recalculates only vehicles currently stored as RESERVED.
     * Cheap enough to run on every GET /api/vehicles and every scheduler tick.
     */
    @Transactional
    public int syncReservedVehicles(Long agencyId) {
        if (agencyId == null) return 0;
        List<Vehicle> reserved = vehicleRepository.findAllByTenantIdAndStatut(agencyId, VehicleStatus.RESERVED);
        LocalDateTime now = LocalDateTime.now();
        int updated = 0;
        for (Vehicle vehicle : reserved) {
            VehicleStatus before = vehicle.getStatut();
            VehicleStatus after = computeAndApply(vehicle, agencyId, now);
            if (after != before) updated++;
        }
        return updated;
    }

    /**
     * Full repair pass for an agency: expires stale reservations, then
     * recalculates every non-terminal vehicle's status. Used by the manual
     * {@code POST /api/vehicles/sync-statuses} repair endpoint.
     */
    @Transactional
    public Map<String, Object> recalculateAgencyVehicles(Long agencyId) {
        int expiredReservations = expireOldReservations(agencyId);

        List<Vehicle> vehicles = vehicleRepository.findAllByTenantId(agencyId);
        LocalDateTime now = LocalDateTime.now();
        int updated = 0;
        for (Vehicle vehicle : vehicles) {
            VehicleStatus before = vehicle.getStatut();
            VehicleStatus after = computeAndApply(vehicle, agencyId, now);
            if (after != before) updated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedVehicles", vehicles.size());
        result.put("updatedVehicles", updated);
        result.put("expiredReservations", expiredReservations);
        return result;
    }

    /** Read-only check: is this vehicle blocked (maintenance, contract, or reservation) right now? */
    @Transactional(readOnly = true)
    public boolean isVehicleBlockedNow(Long vehicleId, LocalDateTime now) {
        Long tenantId = TenantContext.getCurrentTenantId();
        boolean maintenance = maintenanceRepository.existsByTenantIdAndVehicleIdAndStatusIn(
                tenantId, vehicleId, ACTIVE_MAINTENANCE_STATUSES);
        boolean contract = contractRepository.existsActiveVehicleContract(
                vehicleId, tenantId, ALL_ACTIVE_CONTRACT_STATUSES);
        boolean reservation = reservationRepository.existsActiveBlockingReservation(
                vehicleId, tenantId, BLOCKING_RESERVATION_STATUSES, now.toLocalDate(), now.toLocalTime());
        return maintenance || contract || reservation;
    }

    // ── Core recalculation ───────────────────────────────────────────────────

    private VehicleStatus computeAndApply(Vehicle vehicle, Long tenantId, LocalDateTime now) {
        VehicleStatus oldStatus = vehicle.getStatut();

        // Archived / sold / manually out-of-service vehicles are never touched here.
        if (TERMINAL_STATUSES.contains(oldStatus)) {
            return oldStatus;
        }

        Long vehicleId = vehicle.getId();
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        boolean hasActiveMaintenance = maintenanceRepository.existsByTenantIdAndVehicleIdAndStatusIn(
                tenantId, vehicleId, ACTIVE_MAINTENANCE_STATUSES);
        boolean hasRentedContract = contractRepository.existsActiveVehicleContract(
                vehicleId, tenantId, RENTED_CONTRACT_STATUSES);
        boolean hasReservedContract = !hasRentedContract && contractRepository.existsActiveVehicleContract(
                vehicleId, tenantId, RESERVED_CONTRACT_STATUSES);
        boolean hasActiveReservation = reservationRepository.existsActiveBlockingReservation(
                vehicleId, tenantId, BLOCKING_RESERVATION_STATUSES, today, time);

        VehicleStatus newStatus;
        String reason;
        if (hasActiveMaintenance) {
            newStatus = VehicleStatus.MAINTENANCE;
            reason = "ACTIVE_MAINTENANCE";
        } else if (hasRentedContract) {
            newStatus = VehicleStatus.RENTED;
            reason = "ACTIVE_CONTRACT";
        } else if (hasReservedContract) {
            newStatus = VehicleStatus.RESERVED;
            reason = "CONTRACT_AWAITING_SIGNATURE";
        } else if (hasActiveReservation) {
            newStatus = VehicleStatus.RESERVED;
            reason = "ACTIVE_RESERVATION";
        } else {
            newStatus = VehicleStatus.AVAILABLE;
            reason = "NO_ACTIVE_BLOCKERS";
        }

        if (newStatus != oldStatus) {
            vehicle.setStatut(newStatus);
            vehicleRepository.save(vehicle);
        }

        log.info("[VEHICLE_STATUS_SYNC] vehicleId={} agencyId={} oldStatus={} newStatus={} now={} " +
                        "activeReservationCount={} activeContractCount={} activeMaintenanceCount={} reason={}",
                vehicleId, tenantId, oldStatus, newStatus, now,
                hasActiveReservation ? 1 : 0, (hasRentedContract || hasReservedContract) ? 1 : 0,
                hasActiveMaintenance ? 1 : 0, reason);

        return newStatus;
    }
}
