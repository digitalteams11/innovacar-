package com.carrental.service;

import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
        List<Vehicle> candidates = vehicleRepository.findAllByTenantId(tenantId).stream()
                .filter(vehicle -> !EnumSet.of(VehicleStatus.RENTED, VehicleStatus.IN_MAINTENANCE,
                        VehicleStatus.OUT_OF_SERVICE, VehicleStatus.MAINTENANCE).contains(vehicle.getStatut()))
                .toList();

        // Get all active reservations in the date range
        List<Reservation> overlappingReservations = reservationRepository
                .findOverlappingReservations(tenantId, startDate, startTime, endDate, endTime, excludeReservationId);

        // Filter out vehicles with overlapping reservations
        List<Long> reservedVehicleIds = overlappingReservations.stream()
                .map(r -> r.getVehicle().getId())
                .distinct()
                .toList();

        return candidates.stream()
                .filter(v -> !reservedVehicleIds.contains(v.getId()))
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
        Long tenantId = TenantContext.getCurrentTenantId();

        validateRange(startDate, startTime, endDate, endTime);
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId).orElse(null);
        if (vehicle == null || EnumSet.of(VehicleStatus.RENTED, VehicleStatus.IN_MAINTENANCE,
                VehicleStatus.OUT_OF_SERVICE, VehicleStatus.MAINTENANCE).contains(vehicle.getStatut())) {
            return false;
        }

        List<Reservation> overlaps = reservationRepository
                .findOverlappingReservationsForVehicle(
                        tenantId, vehicleId, startDate, startTime, endDate, endTime, excludeReservationId);

        return overlaps.isEmpty();
    }

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
}
