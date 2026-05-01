package com.carrental.service;

import com.carrental.dto.reservation.CreateReservationRequest;
import com.carrental.dto.reservation.ReservationResponse;
import com.carrental.entity.Payment;
import com.carrental.entity.Reservation;
import com.carrental.entity.Vehicle;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reservation-management business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final PaymentRepository paymentRepository;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all reservations for the caller's tenant, optionally filtered by overlapping date range.
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(LocalDate filterStart, LocalDate filterEnd) {
        Long tenantId = TenantContext.getCurrentTenantId();

        List<Reservation> reservations;
        if (filterStart != null && filterEnd != null) {
            if (filterStart.isAfter(filterEnd)) {
                throw new IllegalArgumentException("Filter start date cannot be after end date.");
            }
            reservations = reservationRepository.findOverlappingByTenantAndDates(tenantId, filterStart, filterEnd);
        } else {
            reservations = reservationRepository.findAllByTenantId(tenantId);
        }

        return reservations.stream().map(ReservationResponse::from).toList();
    }

    /**
     * Fetches a single reservation.
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
        return ReservationResponse.from(reservation);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Creates a new reservation. Checks for overlapping dates and calculates total price.
     * Also automatically creates an unpaid payment associated with this reservation.
     */
    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();

        if (request.getDateStart().isAfter(request.getDateEnd())) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(request.getVehicleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

        // Check for double booking
        boolean isOverlapping = reservationRepository.existsOverlappingReservation(
                vehicle.getId(), tenantId, request.getDateStart(), request.getDateEnd());

        if (isOverlapping) {
            throw new IllegalArgumentException("Vehicle is already booked for the selected dates.");
        }

        // Calculate total price: (days + 1) * daily price
        long days = ChronoUnit.DAYS.between(request.getDateStart(), request.getDateEnd()) + 1;
        BigDecimal totalPrice = vehicle.getPrixJour().multiply(BigDecimal.valueOf(days));

        Reservation reservation = reservationRepository.save(Reservation.builder()
                .vehicle(vehicle)
                .dateStart(request.getDateStart())
                .dateEnd(request.getDateEnd())
                .totalPrice(totalPrice)
                .tenant(vehicle.getTenant())
                .build());

        // Automatically generate an unpaid payment for this reservation
        paymentRepository.save(Payment.builder()
                .reservation(reservation)
                .amount(totalPrice)
                .paid(false)
                .tenant(vehicle.getTenant())
                .build());

        log.info("Created reservation [id={}] for vehicle [id={}] in tenant [{}]",
                reservation.getId(), vehicle.getId(), tenantId);

        return ReservationResponse.from(reservation);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes a reservation.
     */
    @Transactional
    public void deleteReservation(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        reservationRepository.delete(reservation);
        log.info("Deleted reservation [id={}] from tenant [{}]", id, tenantId);
    }
}
