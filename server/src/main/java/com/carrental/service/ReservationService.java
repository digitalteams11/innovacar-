package com.carrental.service;

import com.carrental.dto.reservation.CreateReservationRequest;
import com.carrental.dto.reservation.ReservationResponse;
import com.carrental.entity.Client;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationSource;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final AvailabilityService availabilityService;
    private final VehicleStatusSyncService vehicleStatusSyncService;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all reservations for the caller's tenant, optionally filtered by overlapping date range.
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(LocalDate filterStart, LocalDate filterEnd) {
        return getReservations(filterStart, filterEnd, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(
            LocalDate filterStart,
            LocalDate filterEnd,
            String status,
            String search,
            Integer page,
            Integer size) {
        return getReservations(filterStart, filterEnd, status, search, page, size, null);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(
            LocalDate filterStart,
            LocalDate filterEnd,
            String status,
            String search,
            Integer page,
            Integer size,
            Long vehicleId) {
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

        // Belt-and-suspenders: exclude deleted rows even if the repository query somehow
        // returned them. The repository queries filter at DB level; this catches edge cases.
        var stream = reservations.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()));
        if (vehicleId != null) {
            stream = stream.filter(reservation -> reservation.getVehicle() != null
                    && vehicleId.equals(reservation.getVehicle().getId()));
        }
        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase();
            stream = stream.filter(reservation -> reservation.getStatus() != null
                    && reservation.getStatus().name().equals(normalizedStatus));
        }
        if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase();
            stream = stream.filter(reservation ->
                    contains(reservation.getClient() != null ? reservation.getClient().getName() : null, query)
                            || contains(reservation.getClient() != null ? reservation.getClient().getPhone() : null, query)
                            || contains(reservation.getVehicle() != null ? reservation.getVehicle().getMarque() : null, query)
                            || contains(reservation.getVehicle() != null ? reservation.getVehicle().getPlate() : null, query)
                            || contains(String.valueOf(reservation.getId()), query));
        }

        List<ReservationResponse> mapped = stream.map(ReservationResponse::from).toList();
        log.debug("[RESERVATION_LIST_DEBUG] tenantId={} totalReturned={} deletedIncluded=false filter={}",
                tenantId, mapped.size(), status != null ? status : "ALL");
        if (page == null || size == null || page < 0 || size <= 0) {
            return mapped;
        }
        int fromIndex = Math.min(page * size, mapped.size());
        int toIndex = Math.min(fromIndex + size, mapped.size());
        return mapped.subList(fromIndex, toIndex);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
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

    /**
     * Read-only data for prefilling the "New Contract" modal when it's opened
     * from a reservation (icon click or manual reservation-select-in-modal) —
     * never creates or modifies anything.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContractPrefill(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        Client client = reservation.getClient();
        if (client == null) {
            throw new IllegalArgumentException("RESERVATION_CLIENT_MISSING");
        }
        Vehicle vehicle = reservation.getVehicle();
        if (vehicle == null) {
            throw new IllegalArgumentException("RESERVATION_VEHICLE_MISSING");
        }

        LocalTime startTime = reservation.getStartTime() != null ? reservation.getStartTime() : LocalTime.of(9, 0);
        LocalTime endTime = reservation.getEndTime() != null ? reservation.getEndTime() : LocalTime.of(18, 0);
        long durationDays = ChronoUnit.DAYS.between(reservation.getDateStart(), reservation.getDateEnd()) + 1;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reservationId", reservation.getId());
        data.put("reservationNumber", "RES-" + reservation.getId());
        data.put("clientId", client.getId());
        data.put("clientName", client.getName());
        data.put("clientType", client.getCompanyName() != null && !client.getCompanyName().isBlank()
                ? "Company Client" : "Individual Client");
        data.put("clientPhone", client.getPhone());
        data.put("clientEmail", client.getEmail());
        data.put("vehicleId", vehicle.getId());
        data.put("vehicleName", vehicle.getMarque());
        data.put("vehiclePlate", vehicle.getPlate());
        data.put("startDate", reservation.getDateStart());
        data.put("startTime", startTime);
        data.put("endDate", reservation.getDateEnd());
        data.put("endTime", endTime);
        data.put("pickupLocation", reservation.getPickupLocation());
        data.put("returnLocation", reservation.getReturnLocation());
        data.put("durationDays", durationDays);
        data.put("totalAmount", reservation.getTotalPrice());
        data.put("depositAmount", reservation.getDepositAmount() != null
                ? reservation.getDepositAmount()
                : java.util.Optional.ofNullable(vehicle.getDepositAmount()).orElse(BigDecimal.ZERO));
        data.put("paidAmount", reservation.getPaidAmount() != null ? reservation.getPaidAmount() : BigDecimal.ZERO);
        data.put("paymentStatus", reservation.getPaymentStatus() != null ? reservation.getPaymentStatus() : "UNPAID");
        data.put("notes", reservation.getNotes());
        return data;
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
        if (EnumSet.of(VehicleStatus.OUT_OF_SERVICE, VehicleStatus.SOLD, VehicleStatus.ARCHIVED)
                .contains(vehicle.getStatut())) {
            throw new IllegalStateException("Vehicle is not available for reservation in its current status.");
        }

        if (!availabilityService.isVehicleAvailable(
                vehicle.getId(), request.getDateStart(), startTime, request.getDateEnd(), endTime, null)) {
            throw new IllegalArgumentException("Vehicle is already booked or unavailable for the selected dates.");
        }

        // Calculate total price: (days + 1) * daily price
        long days = ChronoUnit.DAYS.between(request.getDateStart(), request.getDateEnd()) + 1;
        BigDecimal totalPrice = vehicle.getPrixJour().multiply(BigDecimal.valueOf(days));
        Reservation.ReservationBuilder builder = Reservation.builder()
                .vehicle(vehicle)
                .dateStart(request.getDateStart())
                .startTime(startTime)
                .dateEnd(request.getDateEnd())
                .endTime(endTime)
                .totalPrice(totalPrice)
                .pickupLocation(request.getPickupLocation())
                .returnLocation(request.getReturnLocation())
                .notes(request.getNotes())
                .status(ReservationStatus.PENDING)
                .source(ReservationSource.MANUAL)
                .paymentStatus(null)
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

        log.info("Created reservation [id={}] for vehicle [id={}] in tenant [{}]",
                reservation.getId(), vehicle.getId(), tenantId);

        return ReservationResponse.from(reservation);
    }

    // ── BACKFILL ──────────────────────────────────────────────────────────────

    /**
     * Safety net for contracts that exist without a linked reservation (e.g. legacy
     * rows created before contracts always carried a reservation). Creates a matching
     * reservation from each orphaned contract's own client/vehicle/dates/price so it
     * shows up on the Reservations page, then links the two records together.
     * Idempotent: only ever touches contracts where {@code reservation IS NULL}, so
     * running it repeatedly cannot create duplicates.
     */
    @Transactional
    public void syncReservationsFromContractsForCurrentTenant() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return;

        List<Contract> orphanedContracts = contractRepository.findAllByTenantIdAndReservationIsNull(tenantId);
        for (Contract contract : orphanedContracts) {
            if (contract.getVehicle() == null || contract.getStartDate() == null || contract.getEndDate() == null) {
                log.warn("Skipping reservation backfill for contract [id={}]: missing vehicle or dates", contract.getId());
                continue;
            }
            Reservation reservation = Reservation.builder()
                    .vehicle(contract.getVehicle())
                    .client(contract.getClient())
                    .dateStart(contract.getStartDate())
                    .startTime(contract.getPickupTime() != null ? contract.getPickupTime() : LocalTime.of(9, 0))
                    .dateEnd(contract.getEndDate())
                    .endTime(contract.getReturnTime() != null ? contract.getReturnTime() : LocalTime.of(18, 0))
                    .totalPrice(contract.getTotalPrice() != null ? contract.getTotalPrice() : BigDecimal.ZERO)
                    .pickupLocation(contract.getPickupLocation())
                    .returnLocation(contract.getReturnLocation())
                    .status(reservationStatusForContractStatus(contract.getStatus()))
                    .source(ReservationSource.AUTO_FROM_CONTRACT)
                    .tenant(contract.getTenant())
                    .build();
            Reservation saved = reservationRepository.save(reservation);
            contract.setReservation(saved);
            contractRepository.save(contract);
            log.info("Backfilled reservation [id={}] for contract [id={}] in tenant [{}]",
                    saved.getId(), contract.getId(), tenantId);
        }
    }

    private ReservationStatus reservationStatusForContractStatus(ContractStatus status) {
        if (status == null) return ReservationStatus.PENDING;
        return switch (status) {
            case DRAFT -> ReservationStatus.PENDING;
            case WAITING_SIGNATURE, WAITING_CLIENT_SIGNATURE, PENDING_SIGNATURE, PARTIALLY_SIGNED, SIGNED ->
                    ReservationStatus.CONFIRMED;
            case ACTIVE, PAID -> ReservationStatus.ACTIVE;
            case COMPLETED -> ReservationStatus.COMPLETED;
            case CANCELLED -> ReservationStatus.CANCELLED;
            case EXPIRED -> ReservationStatus.EXPIRED;
        };
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Soft-deletes a reservation (sets deleted=true, keeps row in DB).
     * Returns a summary map for the controller to return as JSON.
     *
     * Rules:
     *  • Reservation linked to a non-trashed contract → 409 error (delete the contract first).
     *  • AUTO_FROM_CONTRACT reservation whose contract IS trashed → allow soft delete.
     *  • Already deleted → 404 (not visible to tenant).
     */
    @Transactional
    public Map<String, Object> deleteReservation(Long id, String deletedByEmail) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        ReservationStatus beforeStatus = reservation.getStatus();
        String reservationNumber = "RES-" + reservation.getId();
        Contract linkedContract = reservation.getContract();
        Long linkedContractId = linkedContract != null ? linkedContract.getId() : null;
        Boolean linkedContractDeleted = linkedContract != null ? linkedContract.getDeleted() : null;
        String linkedContractStatus = linkedContract != null && linkedContract.getStatus() != null
                ? linkedContract.getStatus().name() : null;

        log.debug("[RESERVATION_DELETE_DEBUG] reservationId={} reservationNumber={} beforeDeleted={} beforeStatus={} linkedContractId={} linkedContractDeleted={} linkedContractStatus={} action=SOFT_DELETE",
                id, reservationNumber, reservation.getDeleted(), beforeStatus,
                linkedContractId, linkedContractDeleted, linkedContractStatus);

        // Block delete if reservation is linked to an active (non-trashed) contract.
        if (linkedContract != null && !Boolean.TRUE.equals(linkedContract.getDeleted())) {
            String contractStatus = linkedContractStatus != null ? linkedContractStatus : "UNKNOWN";
            // Allow if contract is completed or cancelled — those are terminal states.
            boolean terminal = EnumSet.of(ContractStatus.COMPLETED, ContractStatus.CANCELLED)
                    .contains(linkedContract.getStatus());
            if (!terminal) {
                log.warn("[RESERVATION_DELETE_DEBUG] reservationId={} blocked — linked to active contractId={} contractStatus={}",
                        id, linkedContractId, contractStatus);
                throw new IllegalStateException(
                        "RESERVATION_LINKED_TO_ACTIVE_CONTRACT: Cannot delete this reservation because it is linked to an active contract (id=" + linkedContractId + ", status=" + contractStatus + "). Delete or cancel the contract first.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        reservation.setDeleted(true);
        reservation.setDeletedAt(now);
        reservation.setDeletedBy(deletedByEmail);
        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        releaseVehicleIfUnused(reservation);

        log.debug("[RESERVATION_DELETE_DEBUG] reservationId={} reservationNumber={} beforeDeleted=false afterDeleted=true beforeStatus={} afterStatus={} linkedContractId={} linkedContractDeleted={} linkedContractStatus={} action=SOFT_DELETE saved=true",
                id, reservationNumber, beforeStatus, saved.getStatus(),
                linkedContractId, linkedContractDeleted, linkedContractStatus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("deleted", true);
        result.put("deletedAt", now.toString());
        result.put("status", saved.getStatus().name());
        return result;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getDeletedReservations() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return reservationRepository.findAllDeletedByTenantId(tenantId)
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional
    public ReservationResponse updateStatus(Long id, ReservationStatus status) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
        if (reservation.getStatus() == ReservationStatus.CONVERTED_TO_CONTRACT
                || reservation.getContract() != null) {
            throw new IllegalStateException("A converted reservation is read-only.");
        }
        if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCELLED).contains(status)) {
            throw new IllegalArgumentException("Reservation status must be PENDING, CONFIRMED, or CANCELLED.");
        }
        reservation.setStatus(status);
        if (status == ReservationStatus.PENDING || status == ReservationStatus.CONFIRMED) {
            reservation.getVehicle().setStatut(VehicleStatus.RESERVED);
            vehicleRepository.save(reservation.getVehicle());
        } else {
            releaseVehicleIfUnused(reservation);
        }
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse updateReservation(Long id, CreateReservationRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Reservation reservation = reservationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
        if (reservation.getStatus() == ReservationStatus.CONVERTED_TO_CONTRACT
                || reservation.getContract() != null
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
        if (!availabilityService.isVehicleAvailable(
                vehicle.getId(), request.getDateStart(), startTime, request.getDateEnd(), endTime, id)) {
            throw new IllegalArgumentException("Vehicle is already booked or unavailable for the selected date and time.");
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

    /**
     * Recalculates the vehicle's status from real blockers (maintenance, contract,
     * other reservations) instead of blindly forcing AVAILABLE — delegates to
     * {@link VehicleStatusSyncService} so cancelling/completing/editing a
     * reservation never clobbers an active contract or maintenance state.
     */
    private void releaseVehicleIfUnused(Reservation reservation) {
        vehicleStatusSyncService.recalculateVehicleStatus(
                reservation.getVehicle().getId(), reservation.getTenant().getId());
    }
}
