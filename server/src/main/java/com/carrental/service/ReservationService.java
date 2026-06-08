package com.carrental.service;

import com.carrental.dto.reservation.CreateReservationRequest;
import com.carrental.dto.reservation.ReservationResponse;
import com.carrental.entity.Client;
import com.carrental.entity.Contract;
import com.carrental.entity.Deposit;
import com.carrental.entity.Payment;
import com.carrental.entity.PaymentMethod;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.PaymentType;
import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
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
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Reservation-management business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final ClientRepository clientRepository;
    private final ContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final DepositService depositService;
    private final ContractService contractService;

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

        LocalTime startTime = request.getStartTime() != null ? request.getStartTime() : LocalTime.of(9, 0);
        LocalTime endTime = request.getEndTime() != null ? request.getEndTime() : LocalTime.of(18, 0);
        if (request.getDateStart().equals(request.getDateEnd()) && !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("End time must be after start time for a same-day reservation.");
        }

        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(request.getVehicleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));
        if (EnumSet.of(VehicleStatus.RENTED, VehicleStatus.IN_MAINTENANCE,
                VehicleStatus.OUT_OF_SERVICE, VehicleStatus.MAINTENANCE).contains(vehicle.getStatut())) {
            throw new IllegalStateException("Vehicle is not available for reservation in its current status.");
        }

        // Check for double booking
        boolean isOverlapping = reservationRepository.existsOverlappingReservation(
                vehicle.getId(), tenantId, request.getDateStart(), startTime, request.getDateEnd(), endTime);

        if (isOverlapping) {
            throw new IllegalArgumentException("Vehicle is already booked for the selected dates.");
        }

        // Calculate total price: (days + 1) * daily price
        long days = ChronoUnit.DAYS.between(request.getDateStart(), request.getDateEnd()) + 1;
        BigDecimal totalPrice = vehicle.getPrixJour().multiply(BigDecimal.valueOf(days));
        BigDecimal depositAmount = vehicle.getDepositAmount() != null ? vehicle.getDepositAmount() : BigDecimal.ZERO;

        Reservation.ReservationBuilder builder = Reservation.builder()
                .vehicle(vehicle)
                .dateStart(request.getDateStart())
                .startTime(startTime)
                .dateEnd(request.getDateEnd())
                .endTime(endTime)
                .totalPrice(totalPrice)
                .depositAmount(depositAmount)
                .pickupLocation(request.getPickupLocation())
                .returnLocation(request.getReturnLocation())
                .notes(request.getNotes())
                .status(ReservationStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING.name())
                .paidAmount(BigDecimal.ZERO)
                .tenant(vehicle.getTenant());

        // Link client if provided
        if (request.getClientId() != null) {
            Client client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + request.getClientId()));
            builder.client(client);
        }

        Reservation reservation = reservationRepository.save(builder.build());
        vehicle.setStatut(VehicleStatus.RESERVED);
        vehicleRepository.save(vehicle);

        // Automatically generate an unpaid payment for this reservation
        paymentRepository.save(Payment.builder()
                .paymentNumber("RES-" + reservation.getId())
                .reservation(reservation)
                .amount(totalPrice)
                .paymentDate(java.time.LocalDateTime.now())
                .paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PENDING)
                .type(PaymentType.RENTAL)
                .tenant(vehicle.getTenant())
                .build());

        // Create security deposit if required
        Deposit deposit = null;
        boolean depositRequired = request.getDepositRequired() != null ? request.getDepositRequired() : true;
        if (depositRequired && depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            String depositType = request.getDepositType() != null ? request.getDepositType() : "CASH";
            deposit = depositService.createDepositFromReservation(reservation, depositType, request.getDepositReference(), request.getDepositNotes());
        }

        if (reservation.getClient() != null) {
            contractService.createFromReservation(reservation.getId());
        }

        log.info("Created reservation [id={}] for vehicle [id={}] in tenant [{}]",
                reservation.getId(), vehicle.getId(), tenantId);

        return ReservationResponse.from(reservation, deposit != null ? com.carrental.dto.deposit.DepositResponse.from(deposit) : null);
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

        if (reservation.getStatus() == ReservationStatus.ACTIVE) {
            throw new IllegalStateException("An active reservation must be completed before it can be cancelled.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        paymentRepository.findByReservationIdAndTenantId(id, tenantId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        });
        releaseVehicleIfUnused(reservation);
        log.info("Cancelled reservation [id={}] in tenant [{}]", id, tenantId);
    }

    @Transactional
    public ReservationResponse updateStatus(Long id, ReservationStatus status) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
        if (status == ReservationStatus.ACTIVE && reservation.getContract() == null) {
            throw new IllegalStateException("A reservation cannot start without a generated contract.");
        }
        reservation.setStatus(status);
        if (status == ReservationStatus.ACTIVE) {
            reservation.getVehicle().setStatut(VehicleStatus.RENTED);
            vehicleRepository.save(reservation.getVehicle());
        } else if (status == ReservationStatus.PENDING || status == ReservationStatus.CONFIRMED) {
            reservation.getVehicle().setStatut(VehicleStatus.RESERVED);
            vehicleRepository.save(reservation.getVehicle());
        } else if (status == ReservationStatus.COMPLETED || status == ReservationStatus.CANCELLED
                || status == ReservationStatus.EXPIRED) {
            releaseVehicleIfUnused(reservation);
        }
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse updateReservation(Long id, CreateReservationRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
        if (reservation.getStatus() == ReservationStatus.ACTIVE
                || reservation.getStatus() == ReservationStatus.COMPLETED
                || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Only pending or confirmed reservations can be edited.");
        }

        LocalTime startTime = request.getStartTime() != null ? request.getStartTime() : LocalTime.of(9, 0);
        LocalTime endTime = request.getEndTime() != null ? request.getEndTime() : LocalTime.of(18, 0);
        if (request.getDateStart().isAfter(request.getDateEnd())
                || (request.getDateStart().equals(request.getDateEnd()) && !startTime.isBefore(endTime))) {
            throw new IllegalArgumentException("Rental end must be after rental start.");
        }

        Vehicle previousVehicle = reservation.getVehicle();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(request.getVehicleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));
        if (reservationRepository.existsOverlappingReservationExcluding(
                id, vehicle.getId(), tenantId, request.getDateStart(), startTime, request.getDateEnd(), endTime)) {
            throw new IllegalArgumentException("Vehicle is already booked for the selected date and time.");
        }
        Client client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + request.getClientId()));

        long days = ChronoUnit.DAYS.between(request.getDateStart(), request.getDateEnd()) + 1;
        BigDecimal total = vehicle.getPrixJour().multiply(BigDecimal.valueOf(days));
        reservation.setVehicle(vehicle);
        reservation.setClient(client);
        reservation.setDateStart(request.getDateStart());
        reservation.setStartTime(startTime);
        reservation.setDateEnd(request.getDateEnd());
        reservation.setEndTime(endTime);
        reservation.setPickupLocation(request.getPickupLocation());
        reservation.setReturnLocation(request.getReturnLocation());
        reservation.setNotes(request.getNotes());
        reservation.setTotalPrice(total);
        reservation.setDepositAmount(request.getDepositAmount() != null
                ? request.getDepositAmount()
                : Optional.ofNullable(vehicle.getDepositAmount()).orElse(BigDecimal.ZERO));

        if (reservation.getContract() != null) {
            Contract contract = reservation.getContract();
            contract.setClient(client);
            contract.setVehicle(vehicle);
            contract.setStartDate(request.getDateStart());
            contract.setEndDate(request.getDateEnd());
            contract.setPickupTime(startTime);
            contract.setReturnTime(endTime);
            contract.setPickupLocation(request.getPickupLocation());
            contract.setReturnLocation(request.getReturnLocation());
            contract.setTotalPrice(total);
            contract.setRemainingAmount(total.subtract(
                    Optional.ofNullable(contract.getPaidAmount()).orElse(BigDecimal.ZERO)).max(BigDecimal.ZERO));
            contractRepository.save(contract);
        }

        vehicle.setStatut(VehicleStatus.RESERVED);
        vehicleRepository.save(vehicle);
        Reservation saved = reservationRepository.save(reservation);
        if (!previousVehicle.getId().equals(vehicle.getId())) {
            releaseVehicleIfUnused(Reservation.builder()
                    .id(id)
                    .vehicle(previousVehicle)
                    .tenant(reservation.getTenant())
                    .build());
        }
        return ReservationResponse.from(saved);
    }

    private void releaseVehicleIfUnused(Reservation reservation) {
        boolean hasAnotherReservation = reservationRepository.existsByVehicleIdAndTenantIdAndIdNotAndStatusIn(
                reservation.getVehicle().getId(),
                reservation.getTenant().getId(),
                reservation.getId(),
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.ACTIVE));
        reservation.getVehicle().setStatut(hasAnotherReservation ? VehicleStatus.RESERVED : VehicleStatus.AVAILABLE);
        vehicleRepository.save(reservation.getVehicle());
    }
}
