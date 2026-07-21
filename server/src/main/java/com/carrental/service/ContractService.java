package com.carrental.service;

import com.carrental.dto.contract.*;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final ClientRepository clientRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final AdditionalDriverRepository additionalDriverRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final VehicleConditionRepository vehicleConditionRepository;
    private final ContractAuditLogRepository contractAuditLogRepository;
    private final DepositRepository depositRepository;
    private final PaymentRepository paymentRepository;
    private final PdfService pdfService;
    private final NotificationService notificationService;
    private final SseService sseService;
    private final EmailService emailService;
    private final PlatformEmailService platformEmailService;
    private final QrCodeService qrCodeService;
    private final DepositService depositService;
    private final AvailabilityService availabilityService;
    private final VehicleStatusSyncService vehicleStatusSyncService;

    @Autowired(required = false)
    private InspectionService inspectionService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.contracts.trash-retention-days:${app.trash.retention-days:30}}")
    private int trashRetentionDays;

    // ── READ ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractResponse> getAllContracts() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Contract> contracts = contractRepository.findAllByTenantId(tenantId);
        // Batch existence check (one query) instead of an existsById per row —
        // contract.getVehicle().getId() is always safe (no DB hit), so this
        // never triggers the lazy-proxy EntityNotFoundException that broke delete.
        List<Long> vehicleIds = contracts.stream()
                .map(c -> c.getVehicle() != null ? c.getVehicle().getId() : null)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        java.util.Set<Long> existingVehicleIds = vehicleIds.isEmpty()
                ? java.util.Set.of()
                : vehicleRepository.findAllById(vehicleIds).stream().map(Vehicle::getId).collect(Collectors.toSet());
        return contracts.stream()
                .map(contract -> {
                    ContractResponse response = ContractResponse.from(contract);
                    response.setVehicleMissing(response.getVehicleId() != null
                            && !existingVehicleIds.contains(response.getVehicleId()));
                    return response;
                })
                .toList();
    }

    @Transactional
    public ContractResponse getContractById(Long id) {
        Contract contract = fetchContractInTenant(id);
        boolean needsSave = repairSignedStatus(contract);
        if (needsSave) {
            contract = contractRepository.save(contract);
        }
        ContractResponse response = ContractResponse.from(contract);
        response.setVehicleMissing(response.getVehicleId() != null
                && !vehicleRepository.existsById(response.getVehicleId()));
        com.carrental.entity.Deposit deposit = depositRepository.findByContractId(id).orElse(null);
        if (deposit != null) {
            response.setDeposit(com.carrental.dto.deposit.DepositResponse.from(deposit));
        }
        // Attach payment history
        var payments = paymentRepository.findAllByTenantIdAndContractIdOrderByPaymentDateDesc(
                contract.getTenant().getId(), id);
        response.setPayments(payments.stream()
                .map(com.carrental.dto.payment.PaymentResponse::from)
                .collect(Collectors.toList()));
        return response;
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public ContractResponse createContract(CreateContractRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (request.getReservationId() != null) {
            throw new IllegalArgumentException(
                    "Use the reservation conversion endpoint to create a contract from a reservation.");
        }
        java.time.LocalTime pickupTime = request.getPickupTime() != null
                ? request.getPickupTime()
                : request.getStartTime() != null ? request.getStartTime() : java.time.LocalTime.of(9, 0);
        java.time.LocalTime returnTime = request.getReturnTime() != null
                ? request.getReturnTime()
                : request.getEndTime() != null ? request.getEndTime() : java.time.LocalTime.of(18, 0);
        validateRentalPeriod(request.getStartDate(), pickupTime,
                request.getEndDate(), returnTime);
        ContractStatus status = ContractStatus.DRAFT;

        // ── Resolve or auto-create client ──────────────────────────────────────
        if (request.getClientId() == null) {
            throw new IllegalArgumentException("A saved client is required to create a contract.");
        }
        Client client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + request.getClientId()));

        // ── Resolve vehicle ────────────────────────────────────────────────────
        if (request.getVehicleId() == null) {
            throw new IllegalArgumentException("A vehicle is required to create a contract.");
        }
        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(request.getVehicleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle not found with id: " + request.getVehicleId()));
        assertVehicleCanStartDirectRental(vehicle);
        if (!availabilityService.isVehicleAvailable(
                vehicle.getId(), request.getStartDate(), pickupTime, request.getEndDate(), returnTime, null)) {
            throw new IllegalArgumentException("Vehicle is already booked or unavailable for the selected dates.");
        }

        int calculatedRentalDays = Math.toIntExact(
                ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1);
        java.math.BigDecimal resolvedDailyPrice = request.getDailyPrice() != null
                ? request.getDailyPrice() : vehicle.getPrixJour();
        java.math.BigDecimal resolvedDeliveryFees = request.getDeliveryFees() != null
                ? request.getDeliveryFees()
                : java.util.Optional.ofNullable(vehicle.getDeliveryFees()).orElse(java.math.BigDecimal.ZERO);
        java.math.BigDecimal resolvedDiscount = java.util.Optional.ofNullable(request.getDiscountAmount())
                .or(() -> java.util.Optional.ofNullable(request.getDiscount()))
                .orElse(java.math.BigDecimal.ZERO);
        java.math.BigDecimal resolvedTotalPrice = request.getTotalPrice() != null
                ? request.getTotalPrice()
                : resolvedDailyPrice.multiply(java.math.BigDecimal.valueOf(calculatedRentalDays))
                        .add(resolvedDeliveryFees)
                        .subtract(resolvedDiscount)
                        .max(java.math.BigDecimal.ZERO);
        java.math.BigDecimal resolvedPaidAmount = java.util.Optional.ofNullable(request.getPaidAmount())
                .orElse(java.math.BigDecimal.ZERO);
        java.math.BigDecimal resolvedRemainingAmount = request.getRemainingAmount() != null
                ? request.getRemainingAmount()
                : resolvedTotalPrice.subtract(resolvedPaidAmount).max(java.math.BigDecimal.ZERO);
        String resolvedPaymentStatus = StringUtils.hasText(request.getPaymentStatus())
                ? request.getPaymentStatus()
                : resolvedPaidAmount.compareTo(java.math.BigDecimal.ZERO) <= 0
                ? PaymentStatus.PENDING.name()
                : resolvedRemainingAmount.compareTo(java.math.BigDecimal.ZERO) == 0
                ? PaymentStatus.PAID.name()
                : PaymentStatus.PARTIALLY_PAID.name();
        String resolvedContractNumber = StringUtils.hasText(request.getContractNumber())
                ? request.getContractNumber() : generateContractNumber();
        // A previewed number (from GET /contracts/generate-number) can go stale if another
        // contract is created in the meantime — regenerate rather than fail with a conflict.
        if (contractRepository.existsByContractNumberIncludingDeleted(resolvedContractNumber)) {
            resolvedContractNumber = generateContractNumber();
        }

        Reservation reservation = Reservation.builder()
                .vehicle(vehicle)
                .client(client)
                .dateStart(request.getStartDate())
                .startTime(pickupTime)
                .dateEnd(request.getEndDate())
                .endTime(returnTime)
                .totalPrice(resolvedTotalPrice)
                .depositAmount(request.getDepositAmount())
                .paidAmount(resolvedPaidAmount)
                .pickupLocation(request.getPickupLocation())
                .returnLocation(request.getReturnLocation())
                .status(ReservationStatus.CONFIRMED)
                .source(ReservationSource.AUTO_FROM_CONTRACT)
                .paymentStatus(resolvedPaymentStatus)
                .notes(request.getNotes())
                .tenant(tenant)
                .build();
        Reservation savedReservation = reservationRepository.save(reservation);

        return buildAndPersistContract(request, tenant, client, vehicle, savedReservation,
                pickupTime, returnTime, resolvedContractNumber, status, resolvedDailyPrice,
                resolvedTotalPrice, resolvedPaidAmount, resolvedRemainingAmount, resolvedDiscount,
                resolvedPaymentStatus, tenantId);
    }

    /**
     * Builds and persists a {@link Contract} (plus its snapshots, payments,
     * drivers, inspection, and documents) for an already-resolved reservation.
     * Shared by {@link #createContract} (always creates a fresh reservation)
     * and {@link #directCreateContract} (may reuse an existing reservation to
     * stay idempotent).
     */
    private ContractResponse buildAndPersistContract(
            CreateContractRequest request, Tenant tenant, Client client, Vehicle vehicle,
            Reservation savedReservation, java.time.LocalTime pickupTime, java.time.LocalTime returnTime,
            String resolvedContractNumber, ContractStatus status,
            java.math.BigDecimal resolvedDailyPrice, java.math.BigDecimal resolvedTotalPrice,
            java.math.BigDecimal resolvedPaidAmount, java.math.BigDecimal resolvedRemainingAmount,
            java.math.BigDecimal resolvedDiscount, String resolvedPaymentStatus, Long tenantId) {

        int calculatedRentalDays = Math.toIntExact(
                ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1);

        // ── Build contract with auto-populated snapshots ───────────────────────
        Contract.ContractBuilder builder = Contract.builder()
                .contractNumber(resolvedContractNumber)
                .status(status)
                .reservation(savedReservation)
                .contractType(request.getContractType())
                .contractLanguage(request.getContractLanguage())
                .contractDuration(request.getContractDuration())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .pickupDate(request.getPickupDate())
                .returnDate(request.getReturnDate())
                .pickupTime(pickupTime)
                .returnTime(returnTime)
                .pickupLocation(request.getPickupLocation())
                .returnLocation(request.getReturnLocation())
                .totalPrice(resolvedTotalPrice)
                .dailyPrice(resolvedDailyPrice)
                .depositAmount(resolveDepositAmount(request.getDepositAmount()))
                .depositCurrency(request.getDepositCurrency() != null ? request.getDepositCurrency() : "MAD")
                .depositStatus(resolveDepositStatus(request.getDepositStatus(), request.getDepositAmount()))
                .paidAmount(resolvedPaidAmount)
                .remainingAmount(resolvedRemainingAmount)
                .taxAmount(request.getTaxAmount())
                .discountAmount(resolvedDiscount)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(resolvedPaymentStatus)
                .paymentDate(request.getPaymentDate())
                .invoiceNumber(request.getInvoiceNumber())
                .fuelType(request.getFuelType())
                .fuelLevelStart(request.getFuelLevelStart())
                .fuelLevelEnd(request.getFuelLevelEnd())
                .mileageStart(request.getMileageStart())
                .mileageEnd(request.getMileageEnd())
                .notes(request.getNotes())
                .tenant(tenant)
                .termsAccepted(false);

        // Auto-populate client snapshots from linked client
        if (client != null) {
            builder.client(client);
            builder.clientFirstName(client.getName() != null ? client.getName().split(" ", 2)[0] : null);
            builder.clientLastName(client.getName() != null && client.getName().contains(" ")
                    ? client.getName().substring(client.getName().indexOf(" ") + 1) : null);
            builder.clientFullName(client.getName());
            builder.clientName(client.getName());
            builder.clientNationality(client.getNationality());
            builder.clientGender(client.getGender());
            builder.clientBirthDate(client.getBirthDate());
            builder.clientCin(client.getCin());
            builder.clientPassportNumber(client.getPassportNumber());
            builder.clientDriverLicense(client.getDrivingLicense());
            builder.clientDriverLicenseIssue(client.getDrivingLicenseIssue());
            builder.clientDriverLicenseExpiry(client.getDrivingLicenseExpiry());
            builder.clientAddress(client.getAddress());
            builder.clientCity(client.getCity());
            builder.clientCountry(client.getCountry());
            builder.clientPostalCode(client.getPostalCode());
            builder.clientPhone(client.getPhone());
            builder.clientSecondaryPhone(client.getSecondaryPhone());
            builder.clientEmail(client.getEmail());
            builder.emergencyContactName(client.getEmergencyContactName());
            builder.emergencyContactPhone(client.getEmergencyContactPhone());
        } else {
            // Fallback to manual fields if no client could be resolved/created
            builder.clientFirstName(request.getClientFirstName());
            builder.clientLastName(request.getClientLastName());
            builder.clientFullName(request.getClientFullName());
            builder.clientName(request.getClientFullName());
            builder.clientNationality(request.getClientNationality());
            builder.clientGender(request.getClientGender());
            builder.clientBirthDate(request.getClientBirthDate());
            builder.clientCin(request.getClientCin());
            builder.clientPassportNumber(request.getClientPassportNumber());
            builder.clientDriverLicense(request.getClientDriverLicense());
            builder.clientDriverLicenseIssue(request.getClientDriverLicenseIssue());
            builder.clientDriverLicenseExpiry(request.getClientDriverLicenseExpiry());
            builder.clientAddress(request.getClientAddress());
            builder.clientCity(request.getClientCity());
            builder.clientCountry(request.getClientCountry());
            builder.clientPostalCode(request.getClientPostalCode());
            builder.clientPhone(request.getClientPhone());
            builder.clientSecondaryPhone(request.getClientSecondaryPhone());
            builder.clientEmail(request.getClientEmail());
            builder.emergencyContactName(request.getEmergencyContactName());
            builder.emergencyContactPhone(request.getEmergencyContactPhone());
        }

        // Auto-populate vehicle snapshots from linked vehicle
        if (vehicle != null) {
            builder.vehicle(vehicle);
            String[] marqueParts = vehicle.getMarque() != null ? vehicle.getMarque().split(" ", 2) : new String[]{"", ""};
            builder.vehicleBrand(marqueParts[0]);
            builder.vehicleModel(marqueParts.length > 1 ? marqueParts[1] : "");
            builder.vehicleCategory(vehicle.getCategory());
            builder.vehicleYear(vehicle.getYear());
            builder.vehicleColor(vehicle.getColor());
            builder.vehicleRegistration(vehicle.getPlate());
            builder.vehicleVin(vehicle.getGpsImei()); // Using IMEI as VIN fallback
            builder.vehicleTransmission(vehicle.getTransmission());
            builder.insuranceProvider(null);
            builder.insuranceExpiration(vehicle.getInsuranceExpiration());
            builder.technicalInspectionExpiration(vehicle.getTechnicalInspectionExpiration());
            builder.fuelType(vehicle.getFuel());
            if (request.getDailyPrice() == null) {
                builder.dailyPrice(vehicle.getPrixJour());
            }
            if (request.getDepositAmount() == null) {
                builder.depositAmount(vehicle.getDepositAmount());
            }
            if (request.getDeliveryFees() == null) {
                builder.deliveryFees(vehicle.getDeliveryFees());
            }
        } else {
            builder.vehicleBrand(request.getVehicleBrand());
            builder.vehicleModel(request.getVehicleModel());
            builder.vehicleCategory(request.getVehicleCategory());
            builder.vehicleYear(request.getVehicleYear());
            builder.vehicleColor(request.getVehicleColor());
            builder.vehicleRegistration(request.getVehicleRegistration());
            builder.vehicleVin(request.getVehicleVin());
            builder.vehicleTransmission(request.getVehicleTransmission());
            builder.insuranceProvider(request.getInsuranceProvider());
            builder.insuranceExpiration(request.getInsuranceExpiration());
            builder.technicalInspectionExpiration(request.getTechnicalInspectionExpiration());
        }

        builder.pickupAgency(request.getPickupAgency());
        builder.returnAgency(request.getReturnAgency());
        builder.pickupAgent(request.getPickupAgent());
        builder.returnAgent(request.getReturnAgent());
        builder.rentalDays(request.getRentalDays() != null
                ? request.getRentalDays() : calculatedRentalDays);
        builder.extraHours(request.getExtraHours());
        builder.allowedMileage(request.getAllowedMileage());
        builder.extraMileageCost(request.getExtraMileageCost());
        builder.returnFees(request.getReturnFees());
        builder.lateFees(request.getLateFees());
        builder.cleaningFees(request.getCleaningFees());
        builder.fuelCharges(request.getFuelCharges());

        Contract contract = builder.build();
        Contract saved = contractRepository.save(contract);
        savedReservation.setContract(saved);
        reservationRepository.save(savedReservation);
        log.info("[VEHICLE_STATUS_UPDATE] vehicleId={} oldStatus={} newStatus={} operation=DIRECT_CREATE_CONTRACT allowedDbConstraint=vehicles_statut_check",
                vehicle.getId(), vehicle.getStatut(), VehicleStatus.RESERVED);
        vehicle.setStatut(VehicleStatus.RESERVED);
        vehicleRepository.save(vehicle);

        createInitialPaymentIfPresent(saved, savedReservation, client, vehicle, tenant,
                resolvedPaidAmount, resolvedTotalPrice, request.getPaymentMethod());

        // Save related entities
        if (request.getAdditionalDrivers() != null) {
            List<AdditionalDriver> drivers = request.getAdditionalDrivers().stream()
                    .map(d -> AdditionalDriver.builder()
                            .fullName(d.getFullName())
                            .cin(d.getCin())
                            .passportNumber(d.getPassportNumber())
                            .driverLicenseNumber(d.getDriverLicenseNumber())
                            .nationality(d.getNationality())
                            .address(d.getAddress())
                            .phone(d.getPhone())
                            .birthDate(d.getBirthDate())
                            .contract(saved)
                            .build())
                    .collect(Collectors.toList());
            additionalDriverRepository.saveAll(drivers);
        }

        if (request.getVehicleCondition() != null) {
            VehicleConditionDto vc = request.getVehicleCondition();
            VehicleCondition condition = VehicleCondition.builder()
                    .frontDamage(vc.getFrontDamage())
                    .rearDamage(vc.getRearDamage())
                    .leftSideDamage(vc.getLeftSideDamage())
                    .rightSideDamage(vc.getRightSideDamage())
                    .windshieldDamage(vc.getWindshieldDamage())
                    .interiorDamage(vc.getInteriorDamage())
                    .roofDamage(vc.getRoofDamage())
                    .bumperFrontDamage(vc.getBumperFrontDamage())
                    .bumperRearDamage(vc.getBumperRearDamage())
                    .hoodDamage(vc.getHoodDamage())
                    .trunkDamage(vc.getTrunkDamage())
                    .tireCondition(vc.getTireCondition())
                    .scratchDescription(vc.getScratchDescription())
                    .dentDescription(vc.getDentDescription())
                    .generalNotes(vc.getGeneralNotes())
                    .conditionPhotos(vc.getConditionPhotos())
                    .inspectionDate(vc.getInspectionDate())
                    .inspectedBy(vc.getInspectedBy())
                    .isPickupInspection(vc.getIsPickupInspection())
                    .contract(saved)
                    .build();
            vehicleConditionRepository.save(condition);
        }

        if (request.getDocuments() != null) {
            List<ContractDocument> docs = request.getDocuments().stream()
                    .map(d -> ContractDocument.builder()
                            .documentType(d.getDocumentType())
                            .documentName(d.getDocumentName())
                            .documentUrl(d.getDocumentUrl())
                            .isPresent(d.getIsPresent())
                            .verifiedAt(d.getVerifiedAt())
                            .verifiedBy(d.getVerifiedBy())
                            .contract(saved)
                            .build())
                    .collect(Collectors.toList());
            contractDocumentRepository.saveAll(docs);
        }

        // Audit log
        logAudit(saved, "CREATE", "Contract created", null, null);

        log.info("Created contract [id={}] '{}' in tenant [{}]", saved.getId(), saved.getContractNumber(), tenantId);
        return ContractResponse.from(saved);
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    /**
     * Idempotent variant of {@link #createContract}: walk-in / manual contract
     * creation that auto-creates (or reuses) the backing reservation.
     *
     * <ul>
     *   <li>If a matching reservation (by id, or by tenant+client+vehicle+dates)
     *       already has a contract, that existing contract is returned as-is —
     *       no duplicate is created and no conflict is raised.</li>
     *   <li>If a matching reservation exists without a contract yet, the new
     *       contract is linked to it instead of creating a second reservation.</li>
     *   <li>Vehicle availability is only checked against <em>other</em>
     *       bookings — the reservation being converted never conflicts with
     *       itself.</li>
     * </ul>
     */
    @Transactional
    public Map<String, Object> directCreateContract(CreateContractRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        java.time.LocalTime pickupTime = request.getPickupTime() != null
                ? request.getPickupTime() : request.getStartTime() != null ? request.getStartTime() : java.time.LocalTime.of(9, 0);
        java.time.LocalTime returnTime = request.getReturnTime() != null
                ? request.getReturnTime() : request.getEndTime() != null ? request.getEndTime() : java.time.LocalTime.of(18, 0);
        validateRentalPeriod(request.getStartDate(), pickupTime, request.getEndDate(), returnTime);

        log.info("[DIRECT_CREATE_REQUEST] hasClientId={} hasNewClient={} vehicleId={} startDate={} endDate={} startTime={} endTime={} newClientFullName={} newClientPhone={} newClientCin={} newClientLicense={}",
                request.getClientId() != null,
                request.getNewClient() != null,
                request.getVehicleId(),
                request.getStartDate(),
                request.getEndDate(),
                pickupTime,
                returnTime,
                request.getNewClient() != null ? request.getNewClient().getFullName() : null,
                request.getNewClient() != null ? request.getNewClient().getPhone() : null,
                request.getNewClient() != null ? request.getNewClient().getCin() : null,
                request.getNewClient() != null ? request.getNewClient().getDriverLicenseNumber() : null);

        if (request.getClientId() == null && request.getNewClient() == null) {
            throw new IllegalArgumentException("A client is required. Provide clientId or newClient.");
        }
        if (request.getClientId() != null && request.getNewClient() != null) {
            throw new IllegalArgumentException("Provide either clientId or newClient, not both.");
        }
        if (request.getVehicleId() == null) {
            throw new IllegalArgumentException("A vehicle is required to create a contract.");
        }

        // Fetch vehicle first — vehicle errors must surface before client errors so
        // a new-client submission for an already-booked vehicle doesn't fail with
        // CLIENT_ALREADY_EXISTS when the vehicle is the real blocker.
        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdForUpdate(request.getVehicleId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle not found with id: " + request.getVehicleId()));

        // Vehicle status check — immediate fail for archived / out-of-service vehicles.
        assertVehicleCanStartDirectRental(vehicle);

        // For new-client submissions: check vehicle availability BEFORE creating the
        // client so that a vehicle conflict doesn't leave a stale duplicate-check
        // error masking the real VEHICLE_ALREADY_RESERVED reason.
        if (request.getNewClient() != null && request.getReservationId() == null) {
            if (!availabilityService.isVehicleAvailable(
                    vehicle.getId(), request.getStartDate(), pickupTime, request.getEndDate(), returnTime, null)) {
                AvailabilityService.ConflictDetail conflict = availabilityService.findConflictDetail(
                        vehicle.getId(), request.getStartDate(), pickupTime, request.getEndDate(), returnTime, null);
                log.warn("[DIRECT_CREATE_CONFLICT] errorCode=VEHICLE_ALREADY_RESERVED tenantId={} vehicleId={} "
                                + "clientMode=NEW_CLIENT newClientPhone={} newClientCin={} "
                                + "newClientDriverLicense={} startDate={} endDate={} conflictSource={} conflictId={} "
                                + "message=vehicle already reserved",
                        tenantId, vehicle.getId(),
                        request.getNewClient().getPhone(), request.getNewClient().getCin(),
                        request.getNewClient().getDriverLicenseNumber(),
                        request.getStartDate(), request.getEndDate(),
                        conflict.conflictSource(), conflict.conflictId());
                String conflictMessage = conflict.conflictStartDate() != null && conflict.conflictEndDate() != null
                        ? String.format(
                                "This vehicle is already booked from %s to %s (%s #%d). Choose different dates or another vehicle.",
                                conflict.conflictStartDate(), conflict.conflictEndDate(),
                                conflict.conflictSource() != null ? conflict.conflictSource().toLowerCase(java.util.Locale.ROOT) : "booking",
                                conflict.conflictId())
                        : "Vehicle is already reserved for another booking in this period.";
                throw new com.carrental.exception.VehicleConflictException(
                        conflictMessage,
                        "VEHICLE_ALREADY_RESERVED", "vehicleId", vehicle.getId(), "RESERVATION_OR_CONTRACT",
                        request.getStartDate(), request.getEndDate(),
                        conflict.conflictSource(), conflict.conflictId(), conflict.conflictStartDate(), conflict.conflictEndDate());
            }
        }

        boolean clientCreated = false;
        Client client;
        if (request.getClientId() != null) {
            client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Client not found with id: " + request.getClientId()));
        } else {
            try {
                client = createInlineClient(request.getNewClient(), tenant, tenantId);
            } catch (com.carrental.exception.ClientDuplicateException e) {
                log.warn("[DIRECT_CREATE_CONFLICT] errorCode={} tenantId={} vehicleId={} "
                                + "clientMode=NEW_CLIENT newClientPhone={} newClientCin={} "
                                + "newClientDriverLicense={} startDate={} endDate={} matchedFields={} "
                                + "existingClientId={} message={}",
                        e.getErrorCode(), tenantId, vehicle.getId(),
                        request.getNewClient().getPhone(), request.getNewClient().getCin(),
                        request.getNewClient().getDriverLicenseNumber(),
                        request.getStartDate(), request.getEndDate(),
                        e.getMatchedFields(), e.getExistingClientId(), e.getMessage());
                throw e;
            }
            clientCreated = true;
        }

        Reservation existingReservation;
        if (request.getReservationId() != null) {
            existingReservation = reservationRepository.findByIdAndTenantId(request.getReservationId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reservation not found with id: " + request.getReservationId()));
        } else {
            existingReservation = reservationRepository
                    .findFirstByTenantIdAndClientIdAndVehicleIdAndDateStartAndDateEndAndStatusNot(
                            tenantId, client.getId(), vehicle.getId(),
                            request.getStartDate(), request.getEndDate(), ReservationStatus.CANCELLED)
                    .orElse(null);
        }

        // Fallback idempotency check: a contract for this exact tenant+client+
        // vehicle+dates may already exist even if the reservation-based lookup
        // above missed it (e.g. linked to a different/legacy reservation row).
        // A retried/duplicate submission must return the existing contract,
        // never a false 409.
        Contract existingContract = contractRepository
                .findAllByTenantIdAndClientIdAndVehicleIdAndStartDateAndEndDateAndStatusNot(
                        tenantId, client.getId(), vehicle.getId(),
                        request.getStartDate(), request.getEndDate(), ContractStatus.CANCELLED)
                .stream().findFirst().orElse(null);

        log.info("[DIRECT_CREATE] tenantId={} clientId={} vehicleId={} vehicleStatus={} "
                        + "start={} end={} startTime={} endTime={} durationDays={}",
                tenantId, client.getId(), vehicle.getId(), vehicle.getStatut(),
                request.getStartDate(), request.getEndDate(), pickupTime, returnTime,
                ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1);
        log.info("[DIRECT_CREATE] sameReservation={} sameReservationHasContract={} sameContract={}",
                existingReservation != null,
                existingReservation != null && existingReservation.getContract() != null,
                existingContract != null);

        if (existingReservation != null && existingReservation.getContract() != null) {
            log.info("[DIRECT_CREATE] returning existing contract via reservation link "
                            + "contractId={} reservationId={}",
                    existingReservation.getContract().getId(), existingReservation.getId());
            return existingContractPayload(existingReservation, existingReservation.getContract());
        }

        if (existingContract != null) {
            log.info("[DIRECT_CREATE] returning existing contract via fallback client+vehicle+dates lookup "
                            + "contractId={}", existingContract.getId());
            Reservation linkedReservation = existingContract.getReservation() != null
                    ? existingContract.getReservation() : existingReservation;
            return existingContractPayload(linkedReservation, existingContract);
        }

        // Detect orphaned AUTO_FROM_CONTRACT reservations whose backing contract was soft-deleted.
        // @SQLRestriction hides the deleted contract from existingReservation.getContract(), yet
        // the DB still holds contracts.reservation_id = existingReservation.id — creating a new
        // contract would hit the unique constraint on that column, surfacing as DATA_CONFLICT 409.
        // Cancel the orphaned reservation and treat it as absent so a fresh one is created.
        if (existingReservation != null && existingReservation.getContract() == null
                && existingReservation.getSource() == ReservationSource.AUTO_FROM_CONTRACT
                && contractRepository.existsByReservationIdIncludingDeleted(existingReservation.getId())) {
            log.info("[DIRECT_CREATE] orphaned reservation reservationId={} — "
                    + "backing contract was trashed, cancelling and ignoring to prevent "
                    + "contracts.reservation_id unique constraint violation",
                    existingReservation.getId());
            existingReservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(existingReservation);
            existingReservation = null;
        }

        // For existing-client submissions the availability check runs here (after
        // the idempotency gate) because a pre-existing reservation for this exact
        // client+vehicle+dates pair is a valid slot — the gate already returned early.
        if (!clientCreated && existingReservation == null
                && !availabilityService.isVehicleAvailable(
                        vehicle.getId(), request.getStartDate(), pickupTime, request.getEndDate(), returnTime, null)) {
            AvailabilityService.ConflictDetail conflict = availabilityService.findConflictDetail(
                    vehicle.getId(), request.getStartDate(), pickupTime, request.getEndDate(), returnTime, null);
            log.warn("[DIRECT_CREATE_CONFLICT] errorCode=VEHICLE_ALREADY_RESERVED tenantId={} vehicleId={} "
                            + "clientMode=EXISTING_CLIENT startDate={} endDate={} "
                            + "conflictSource={} conflictId={} conflictStart={} conflictEnd={} "
                            + "message=vehicle already reserved",
                    tenantId, vehicle.getId(),
                    request.getStartDate(), request.getEndDate(),
                    conflict.conflictSource(), conflict.conflictId(),
                    conflict.conflictStartDate(), conflict.conflictEndDate());
            String conflictMessage = conflict.conflictStartDate() != null && conflict.conflictEndDate() != null
                    ? String.format(
                            "This vehicle is already booked from %s to %s (%s #%d). Choose different dates or another vehicle.",
                            conflict.conflictStartDate(), conflict.conflictEndDate(),
                            conflict.conflictSource() != null ? conflict.conflictSource().toLowerCase(java.util.Locale.ROOT) : "booking",
                            conflict.conflictId())
                    : "Vehicle is already reserved for another booking in this period.";
            throw new com.carrental.exception.VehicleConflictException(
                    conflictMessage,
                    "VEHICLE_ALREADY_RESERVED", "vehicleId", vehicle.getId(), "RESERVATION_OR_CONTRACT",
                    request.getStartDate(), request.getEndDate(),
                    conflict.conflictSource(), conflict.conflictId(), conflict.conflictStartDate(), conflict.conflictEndDate());
        }
        log.info("[DIRECT_CREATE] no conflict found — proceeding to create contract");

        java.math.BigDecimal resolvedDailyPrice = request.getDailyPrice() != null
                ? request.getDailyPrice() : vehicle.getPrixJour();
        if (resolvedDailyPrice == null) {
            throw new IllegalArgumentException(
                    "This vehicle has no daily price. Update vehicle pricing before creating a contract.");
        }
        java.math.BigDecimal resolvedDeliveryFees = request.getDeliveryFees() != null
                ? request.getDeliveryFees()
                : java.util.Optional.ofNullable(vehicle.getDeliveryFees()).orElse(java.math.BigDecimal.ZERO);
        java.math.BigDecimal resolvedDiscount = java.util.Optional.ofNullable(request.getDiscountAmount())
                .or(() -> java.util.Optional.ofNullable(request.getDiscount()))
                .orElse(java.math.BigDecimal.ZERO);
        int calculatedRentalDays = Math.toIntExact(
                ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1);
        java.math.BigDecimal resolvedTotalPrice = request.getTotalPrice() != null
                ? request.getTotalPrice()
                : resolvedDailyPrice.multiply(java.math.BigDecimal.valueOf(calculatedRentalDays))
                        .add(resolvedDeliveryFees)
                        .subtract(resolvedDiscount)
                        .max(java.math.BigDecimal.ZERO);
        java.math.BigDecimal resolvedPaidAmount = java.util.Optional.ofNullable(request.getPaidAmount())
                .orElse(java.math.BigDecimal.ZERO);
        java.math.BigDecimal resolvedRemainingAmount = request.getRemainingAmount() != null
                ? request.getRemainingAmount()
                : resolvedTotalPrice.subtract(resolvedPaidAmount).max(java.math.BigDecimal.ZERO);
        String resolvedPaymentStatus = StringUtils.hasText(request.getPaymentStatus())
                ? request.getPaymentStatus()
                : resolvedPaidAmount.compareTo(java.math.BigDecimal.ZERO) <= 0
                ? PaymentStatus.PENDING.name()
                : resolvedRemainingAmount.compareTo(java.math.BigDecimal.ZERO) == 0
                ? PaymentStatus.PAID.name()
                : PaymentStatus.PARTIALLY_PAID.name();
        String resolvedContractNumber = StringUtils.hasText(request.getContractNumber())
                ? request.getContractNumber() : generateContractNumber();
        if (contractRepository.existsByContractNumberIncludingDeleted(resolvedContractNumber)) {
            resolvedContractNumber = generateContractNumber();
        }
        var currentAuth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String currentUserEmail = currentAuth != null ? currentAuth.getName() : null;
        log.info("[DIRECT_CREATE_BACKEND] currentUser={} tenantId={} clientId={} clientTenantId={} "
                        + "vehicleId={} vehicleTenantId={} vehicleStatus={} vehicleDeleted={} dailyPrice={} "
                        + "generatedContractNumber={}",
                currentUserEmail, tenantId, client.getId(),
                client.getTenant() != null ? client.getTenant().getId() : null,
                vehicle.getId(), vehicle.getTenant() != null ? vehicle.getTenant().getId() : null,
                vehicle.getStatut(), vehicle.getDeleted(), resolvedDailyPrice, resolvedContractNumber);

        // Pre-save diagnostic log — captured before any validation throw so the root cause
        // of any subsequent 409 is always visible in the server log.
        log.info("[DIRECT_CREATE_409_ROOT] tenantId={} userId={} contractNumber={} contractNumberExists={} "
                        + "vehicleId={} vehicleExists=true vehicleDeleted={} vehicleStatus={} "
                        + "clientId={} clientExists=true "
                        + "startDate={} endDate={} startTime={} endTime={} "
                        + "dailyPrice={} rentalDays={} totalAmount={} paidAmount={} "
                        + "paidAmountExceedsTotal={} "
                        + "reusingReservationId={} existingContractId=null",
                tenantId, currentUserEmail, resolvedContractNumber,
                contractRepository.existsByContractNumberIncludingDeleted(resolvedContractNumber),
                vehicle.getId(), vehicle.getDeleted(), vehicle.getStatut(),
                client.getId(),
                request.getStartDate(), request.getEndDate(), pickupTime, returnTime,
                resolvedDailyPrice, calculatedRentalDays, resolvedTotalPrice, resolvedPaidAmount,
                resolvedPaidAmount.compareTo(resolvedTotalPrice) > 0,
                existingReservation != null ? existingReservation.getId() : null);

        // PAID_AMOUNT_EXCEEDS_TOTAL — validate before saving any data.
        if (resolvedPaidAmount.compareTo(resolvedTotalPrice) > 0) {
            log.warn("[DIRECT_CREATE_409_ROOT] finalErrorCode=PAID_AMOUNT_EXCEEDS_TOTAL "
                            + "paidAmount={} totalAmount={}",
                    resolvedPaidAmount, resolvedTotalPrice);
            throw new com.carrental.exception.PaidAmountExceedsTotalException(resolvedPaidAmount, resolvedTotalPrice);
        }

        Reservation savedReservation;
        if (existingReservation != null) {
            if (existingReservation.getStatus() != ReservationStatus.CONFIRMED) {
                existingReservation.setStatus(ReservationStatus.CONFIRMED);
            }
            savedReservation = reservationRepository.save(existingReservation);
        } else {
            Reservation reservation = Reservation.builder()
                    .vehicle(vehicle)
                    .client(client)
                    .dateStart(request.getStartDate())
                    .startTime(pickupTime)
                    .dateEnd(request.getEndDate())
                    .endTime(returnTime)
                    .totalPrice(resolvedTotalPrice)
                    .depositAmount(request.getDepositAmount())
                    .paidAmount(resolvedPaidAmount)
                    .pickupLocation(request.getPickupLocation())
                    .returnLocation(request.getReturnLocation())
                    .status(ReservationStatus.CONFIRMED)
                    .source(ReservationSource.AUTO_FROM_CONTRACT)
                    .paymentStatus(resolvedPaymentStatus)
                    .notes(request.getNotes())
                    .tenant(tenant)
                    .build();
            savedReservation = reservationRepository.save(reservation);
        }

        ContractResponse contract = buildAndPersistContract(request, tenant, client, vehicle, savedReservation,
                pickupTime, returnTime, resolvedContractNumber, ContractStatus.PENDING_SIGNATURE, resolvedDailyPrice,
                resolvedTotalPrice, resolvedPaidAmount, resolvedRemainingAmount, resolvedDiscount,
                resolvedPaymentStatus, tenantId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contractId", contract.getId());
        data.put("contractNumber", contract.getContractNumber());
        data.put("reservationId", contract.getReservationId());
        data.put("contractStatus", contract.getStatus() != null ? contract.getStatus().name() : ContractStatus.PENDING_SIGNATURE.name());
        data.put("reservationStatus", ReservationStatus.CONFIRMED.name());
        data.put("clientId", client.getId());
        data.put("clientName", client.getName());
        data.put("clientCreated", clientCreated);
        data.put("vehicleId", vehicle.getId());
        data.put("isNew", true);
        return Map.of(
                "success", true,
                "message", "Contract created successfully.",
                "data", data
        );
    }

    /**
     * Creates a new Client entity from inline data supplied in the contract request.
     * Called inside directCreateContract — participates in the same @Transactional scope,
     * so a later failure rolls back the client row automatically.
     */
    private Client createInlineClient(com.carrental.dto.contract.NewClientInlineRequest nc, Tenant tenant, Long tenantId) {
        // Required-field validation
        if (!StringUtils.hasText(nc.getFullName())) {
            throw new IllegalArgumentException("newClient.fullName is required");
        }
        if (!StringUtils.hasText(nc.getPhone())) {
            throw new IllegalArgumentException("newClient.phone is required");
        }
        if (!StringUtils.hasText(nc.getCin()) && !StringUtils.hasText(nc.getDriverLicenseNumber())) {
            throw new IllegalArgumentException("newClient.cin or driverLicenseNumber is required");
        }

        // Collect ALL duplicate-field matches before throwing so the caller sees every
        // conflicting field at once instead of cycling through them one per submission.
        java.util.List<String> matchedFields = new java.util.ArrayList<>();
        Long[] dupId   = {null};
        String[] dupName  = {null};
        String[] dupPhone = {null};

        // phone — always checked (required field)
        clientRepository.findFirstByTenantIdAndPhoneIgnoreCaseAndDeletedFalse(tenantId, nc.getPhone().trim())
                .ifPresent(m -> {
                    matchedFields.add("phone");
                    if (dupId[0] == null) { dupId[0] = m.getId(); dupName[0] = m.getName(); dupPhone[0] = m.getPhone(); }
                });

        // CIN
        if (StringUtils.hasText(nc.getCin())) {
            clientRepository.findFirstByTenantIdAndCinIgnoreCaseAndDeletedFalse(tenantId, nc.getCin().trim())
                    .ifPresent(m -> {
                        matchedFields.add("cin");
                        if (dupId[0] == null) { dupId[0] = m.getId(); dupName[0] = m.getName(); dupPhone[0] = m.getPhone(); }
                    });
        }

        // passport
        if (StringUtils.hasText(nc.getPassportNumber())) {
            clientRepository.findFirstByTenantIdAndPassportNumberIgnoreCaseAndDeletedFalse(tenantId, nc.getPassportNumber().trim())
                    .ifPresent(m -> {
                        matchedFields.add("passportNumber");
                        if (dupId[0] == null) { dupId[0] = m.getId(); dupName[0] = m.getName(); dupPhone[0] = m.getPhone(); }
                    });
        }

        // driver license
        if (StringUtils.hasText(nc.getDriverLicenseNumber())) {
            clientRepository.findFirstByTenantIdAndDrivingLicenseIgnoreCaseAndDeletedFalse(tenantId, nc.getDriverLicenseNumber().trim())
                    .ifPresent(m -> {
                        matchedFields.add("driverLicenseNumber");
                        if (dupId[0] == null) { dupId[0] = m.getId(); dupName[0] = m.getName(); dupPhone[0] = m.getPhone(); }
                    });
        }

        // email
        if (StringUtils.hasText(nc.getEmail())) {
            clientRepository.findFirstByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, nc.getEmail().trim())
                    .ifPresent(m -> {
                        matchedFields.add("email");
                        if (dupId[0] == null) { dupId[0] = m.getId(); dupName[0] = m.getName(); dupPhone[0] = m.getPhone(); }
                    });
        }

        if (!matchedFields.isEmpty()) {
            throw new com.carrental.exception.ClientDuplicateException(
                    "A client with the same " + String.join(", ", matchedFields) + " already exists.",
                    "CLIENT_ALREADY_EXISTS", matchedFields.get(0),
                    dupId[0], dupName[0], dupPhone[0],
                    java.util.Collections.unmodifiableList(matchedFields));
        }

        Client newClient = Client.builder()
                .name(nc.getFullName().trim())
                .phone(nc.getPhone().trim())
                .email(StringUtils.hasText(nc.getEmail()) ? nc.getEmail().trim() : null)
                .cin(StringUtils.hasText(nc.getCin()) ? nc.getCin().trim() : null)
                .passportNumber(StringUtils.hasText(nc.getPassportNumber()) ? nc.getPassportNumber().trim() : null)
                .drivingLicense(StringUtils.hasText(nc.getDriverLicenseNumber()) ? nc.getDriverLicenseNumber().trim() : null)
                .address(StringUtils.hasText(nc.getAddress()) ? nc.getAddress().trim() : null)
                .birthDate(nc.getDateOfBirth())
                .nationality(StringUtils.hasText(nc.getNationality()) ? nc.getNationality().trim() : null)
                .notes(StringUtils.hasText(nc.getNotes()) ? nc.getNotes().trim() : null)
                .tenant(tenant)
                .deleted(false)
                .build();
        Client saved = clientRepository.save(newClient);
        log.info("[INLINE_CLIENT_CREATE] tenantId={} newClientId={} name={} phone={}",
                tenantId, saved.getId(), saved.getName(), saved.getPhone());
        return saved;
    }

    private Map<String, Object> existingContractPayload(Reservation reservation, Contract existing) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contractId", existing.getId());
        data.put("contractNumber", existing.getContractNumber());
        data.put("reservationId", reservation != null ? reservation.getId()
                : existing.getReservation() != null ? existing.getReservation().getId() : null);
        data.put("contractStatus", existing.getStatus() != null ? existing.getStatus().name() : ContractStatus.DRAFT.name());
        data.put("reservationStatus", reservation != null && reservation.getStatus() != null
                ? reservation.getStatus().name() : ReservationStatus.CONFIRMED.name());
        data.put("isNew", false);
        return Map.of(
                "success", true,
                "message", "Existing contract loaded.",
                "data", data
        );
    }

    private void createInitialPaymentIfPresent(Contract contract, Reservation reservation, Client client, Vehicle vehicle,
                                               Tenant tenant, java.math.BigDecimal paidAmount,
                                               java.math.BigDecimal totalAmount, String requestedMethod) {
        if (paidAmount == null || paidAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return;
        }

        PaymentMethod method = parsePaymentMethod(requestedMethod);
        PaymentStatus status = paidAmount.compareTo(totalAmount) >= 0
                ? PaymentStatus.PAID
                : PaymentStatus.PARTIALLY_PAID;

        Payment payment = Payment.builder()
                .paymentNumber("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .amount(paidAmount)
                .paymentDate(LocalDateTime.now())
                .paymentMethod(method)
                .status(status)
                .paid(status == PaymentStatus.PAID)
                .type(PaymentType.RENTAL)
                .reservation(reservation)
                .contract(contract)
                .client(client)
                .vehicle(vehicle)
                .tenant(tenant)
                .notes("Initial payment recorded during direct contract creation")
                .build();
        payment.setPaid(status == PaymentStatus.PAID);
        paymentRepository.save(payment);
    }

    private PaymentMethod parsePaymentMethod(String value) {
        if (!StringUtils.hasText(value)) {
            return PaymentMethod.CASH;
        }
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PaymentMethod.OTHER;
        }
    }

    @Transactional
    public ContractResponse updateContract(Long id, UpdateContractRequest request) {
        Contract contract = fetchContractInTenant(id);

        updateField(request.getContractNumber(), contract::setContractNumber);
        if (request.getStatus() != null) contract.setStatus(request.getStatus());
        updateField(request.getContractType(), contract::setContractType);
        updateField(request.getContractLanguage(), contract::setContractLanguage);
        if (request.getContractDuration() != null) contract.setContractDuration(request.getContractDuration());

        if (request.getClientId() != null) {
            Long tenantId = TenantContext.getCurrentTenantId();
            clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                    .ifPresent(contract::setClient);
        }
        if (request.getVehicleId() != null) {
            Long tenantId = TenantContext.getCurrentTenantId();
            vehicleRepository.findByIdAndTenantId(request.getVehicleId(), tenantId)
                    .ifPresent(contract::setVehicle);
        }

        updateField(request.getStartDate(), contract::setStartDate);
        updateField(request.getEndDate(), contract::setEndDate);
        updateField(request.getPickupDate(), contract::setPickupDate);
        updateField(request.getReturnDate(), contract::setReturnDate);
        updateField(request.getPickupTime(), contract::setPickupTime);
        updateField(request.getReturnTime(), contract::setReturnTime);
        updateField(request.getPickupLocation(), contract::setPickupLocation);
        updateField(request.getReturnLocation(), contract::setReturnLocation);

        updateField(request.getClientFirstName(), contract::setClientFirstName);
        updateField(request.getClientLastName(), contract::setClientLastName);
        updateField(request.getClientFullName(), contract::setClientFullName);
        updateField(request.getClientNationality(), contract::setClientNationality);
        updateField(request.getClientGender(), contract::setClientGender);
        updateField(request.getClientBirthDate(), contract::setClientBirthDate);
        updateField(request.getClientCin(), contract::setClientCin);
        updateField(request.getClientPassportNumber(), contract::setClientPassportNumber);
        updateField(request.getClientDriverLicense(), contract::setClientDriverLicense);
        updateField(request.getClientDriverLicenseIssue(), contract::setClientDriverLicenseIssue);
        updateField(request.getClientDriverLicenseExpiry(), contract::setClientDriverLicenseExpiry);
        updateField(request.getClientAddress(), contract::setClientAddress);
        updateField(request.getClientCity(), contract::setClientCity);
        updateField(request.getClientCountry(), contract::setClientCountry);
        updateField(request.getClientPostalCode(), contract::setClientPostalCode);
        updateField(request.getClientPhone(), contract::setClientPhone);
        updateField(request.getClientSecondaryPhone(), contract::setClientSecondaryPhone);
        updateField(request.getClientEmail(), contract::setClientEmail);
        updateField(request.getEmergencyContactName(), contract::setEmergencyContactName);
        updateField(request.getEmergencyContactPhone(), contract::setEmergencyContactPhone);

        updateField(request.getVehicleBrand(), contract::setVehicleBrand);
        updateField(request.getVehicleModel(), contract::setVehicleModel);
        updateField(request.getVehicleCategory(), contract::setVehicleCategory);
        if (request.getVehicleYear() != null) contract.setVehicleYear(request.getVehicleYear());
        updateField(request.getVehicleColor(), contract::setVehicleColor);
        updateField(request.getVehicleRegistration(), contract::setVehicleRegistration);
        updateField(request.getVehicleVin(), contract::setVehicleVin);
        updateField(request.getVehicleTransmission(), contract::setVehicleTransmission);
        updateField(request.getInsuranceProvider(), contract::setInsuranceProvider);
        updateField(request.getInsuranceExpiration(), contract::setInsuranceExpiration);
        updateField(request.getTechnicalInspectionExpiration(), contract::setTechnicalInspectionExpiration);

        updateField(request.getPickupAgency(), contract::setPickupAgency);
        updateField(request.getReturnAgency(), contract::setReturnAgency);
        updateField(request.getPickupAgent(), contract::setPickupAgent);
        updateField(request.getReturnAgent(), contract::setReturnAgent);
        if (request.getRentalDays() != null) contract.setRentalDays(request.getRentalDays());
        if (request.getExtraHours() != null) contract.setExtraHours(request.getExtraHours());
        if (request.getAllowedMileage() != null) contract.setAllowedMileage(request.getAllowedMileage());
        updateField(request.getExtraMileageCost(), contract::setExtraMileageCost);
        updateField(request.getDeliveryFees(), contract::setDeliveryFees);
        updateField(request.getReturnFees(), contract::setReturnFees);
        updateField(request.getLateFees(), contract::setLateFees);
        updateField(request.getCleaningFees(), contract::setCleaningFees);
        updateField(request.getFuelCharges(), contract::setFuelCharges);

        updateField(request.getTotalPrice(), contract::setTotalPrice);
        updateField(request.getDailyPrice(), contract::setDailyPrice);
        if (request.getDepositAmount() != null) {
            if (request.getDepositAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("INVALID_DEPOSIT_AMOUNT");
            }
            contract.setDepositAmount(request.getDepositAmount());
            // Re-derive status when amount changes (only if not explicitly provided)
            if (request.getDepositStatus() == null && contract.getDepositStatus() == com.carrental.entity.DepositStatus.NOT_REQUIRED
                    && request.getDepositAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                contract.setDepositStatus(com.carrental.entity.DepositStatus.PENDING);
            }
        }
        updateField(request.getDepositCurrency(), contract::setDepositCurrency);
        if (request.getDepositStatus() != null) contract.setDepositStatus(request.getDepositStatus());
        updateField(request.getPaidAmount(), contract::setPaidAmount);
        updateField(request.getRemainingAmount(), contract::setRemainingAmount);
        updateField(request.getTaxAmount(), contract::setTaxAmount);
        updateField(request.getDiscountAmount(), contract::setDiscountAmount);
        updateField(request.getPaymentMethod(), contract::setPaymentMethod);
        updateField(request.getPaymentStatus(), contract::setPaymentStatus);
        updateField(request.getPaymentDate(), contract::setPaymentDate);
        updateField(request.getInvoiceNumber(), contract::setInvoiceNumber);

        updateField(request.getFuelType(), contract::setFuelType);
        updateField(request.getFuelLevelStart(), contract::setFuelLevelStart);
        updateField(request.getFuelLevelEnd(), contract::setFuelLevelEnd);
        if (request.getMileageStart() != null) contract.setMileageStart(request.getMileageStart());
        if (request.getMileageEnd() != null) contract.setMileageEnd(request.getMileageEnd());

        updateField(request.getNotes(), contract::setNotes);

        Contract saved = contractRepository.save(contract);
        logAudit(saved, "UPDATE", "Contract updated", null, null);

        log.info("Updated contract [id={}] in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return ContractResponse.from(saved);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> deleteContract(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        String currentUser = getCurrentUser();
        log.debug("[CONTRACT_DELETE_DEBUG] contractId={} currentUserId={} agencyId={}", id, currentUser, tenantId);

        Optional<Contract> found = contractRepository.findByIdAndTenantId(id, tenantId);
        log.debug("[CONTRACT_DELETE_DEBUG] foundByIdAndAgency={}", found.isPresent());

        if (found.isEmpty()) {
            // Distinguish "doesn't exist", "belongs to another agency", and
            // "already deleted" for diagnosis — findRawStateById bypasses the
            // @SQLRestriction soft-delete filter that every normal query on
            // Contract applies automatically.
            contractRepository.findRawStateById(id).ifPresentOrElse(row -> {
                Long rawTenantId = row[0] == null ? null : ((Number) row[0]).longValue();
                Boolean rawDeleted = row[1] != null && (Boolean) row[1];
                log.debug("[CONTRACT_DELETE_DEBUG] foundById=true rawTenantId={} deletedFlag={} status={}",
                        rawTenantId, rawDeleted, row[2]);
            }, () -> log.debug("[CONTRACT_DELETE_DEBUG] foundById=false — contract does not exist"));
            throw new ResourceNotFoundException("Contract not found with id: " + id);
        }

        Contract contract = found.get();
        // contract.getVehicle() only returns the lazy proxy / FK id here — it
        // is never touched beyond getId(), which Hibernate resolves from the
        // proxy's identifier without a DB hit, so a vehicle that was since
        // soft-deleted (and is therefore invisible to every normal query via
        // @SQLRestriction) can never throw EntityNotFoundException here.
        Long vehicleId = contract.getVehicle() != null ? contract.getVehicle().getId() : null;
        boolean vehicleExists = vehicleId != null && vehicleRepository.existsById(vehicleId);
        log.debug("[CONTRACT_DELETE_DEBUG] contractNumber={} vehicleId={} vehicleExists={} deletedFlag={}",
                contract.getContractNumber(), vehicleId, vehicleExists, contract.getDeleted());

        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw new IllegalStateException("Completed contracts cannot be moved to trash");
        }
        ContractStatus statusBeforeDelete = contract.getStatus();
        LocalDateTime deletedAt = LocalDateTime.now();
        log.debug("[CONTRACT_DELETE_STATUS_DEBUG] contractId={} contractNumber={} beforeContractStatus={} deletedBefore={} action=SOFT_DELETE",
                id, contract.getContractNumber(), statusBeforeDelete, contract.getDeleted());

        // Preserve the business status so restore can put the contract back exactly
        // where it was. Do NOT change contractStatus to CANCELLED — cancellation is a
        // separate business action triggered only by the explicit cancel endpoint.
        contract.setStatusBeforeDelete(statusBeforeDelete);
        contract.setDeleted(true);
        contract.setDeletedAt(deletedAt);
        contract.setDeletedBy(currentUser);
        // Vehicle: recalculate — if there is still a CONFIRMED/PENDING backing reservation
        // the vehicle stays RESERVED; if none remain it becomes AVAILABLE. Either is correct.
        releaseVehicleAfterContractSafely(vehicleId, tenantId);

        // Do NOT cancel the linked reservation during trash. The reservation keeps its
        // current status so that:
        //   • the reservation list does not show a spurious CANCELLED entry for
        //     contracts that are only temporarily trashed, and
        //   • restore does not need to figure out the original reservation status.
        // If previousReservationStatus was set by older code (which did cancel it),
        // restoreContract() will still restore it — backward compat is preserved.

        Contract saved = contractRepository.save(contract);

        // Safety assertion: contract_status must NOT change during a soft-delete.
        if (saved.getStatus() != statusBeforeDelete) {
            log.error("[CONTRACT_DELETE_STATUS_DEBUG] contractId={} contractNumber={} STATUS_CHANGED_DURING_DELETE! beforeStatus={} afterStatus={} — this is a bug",
                    id, saved.getContractNumber(), statusBeforeDelete, saved.getStatus());
        }
        log.debug("[CONTRACT_DELETE_STATUS_DEBUG] contractId={} contractNumber={} beforeContractStatus={} afterContractStatus={} deletedBefore=false deletedAfter=true action=SOFT_DELETE statusUnchanged={}",
                id, saved.getContractNumber(), statusBeforeDelete, saved.getStatus(), statusBeforeDelete == saved.getStatus());
        log.info("[CONTRACT_ACTION] action=SOFT_DELETE contractId={} contractNumber={} tenantId={} beforeStatus={} afterStatus={} deletedBefore=false deletedAfter=true",
                id, saved.getContractNumber(), tenantId, statusBeforeDelete, saved.getStatus());
        logAudit(saved, "TRASH", "Contract moved to trash", statusBeforeDelete.name(), statusBeforeDelete.name());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("contractNumber", saved.getContractNumber());
        result.put("contractStatus", statusBeforeDelete.name());
        result.put("deleted", true);
        result.put("deletedAt", deletedAt);
        result.put("restorableUntil", deletedAt.plusDays(trashRetentionDays));
        result.put("vehicleMissing", vehicleId != null && !vehicleExists);
        return result;
    }

    // ── TRASH / RESTORE ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrashedContracts() {
        Long tenantId = TenantContext.getCurrentTenantId();
        LocalDateTime now = LocalDateTime.now();
        return contractRepository.findAllTrashedByTenantId(tenantId).stream()
                .filter(c -> c.getDeletedAt() != null && c.getDeletedAt().plusDays(trashRetentionDays).isAfter(now))
                .map(this::toTrashItem)
                .toList();
    }

    private Map<String, Object> toTrashItem(Contract contract) {
        LocalDateTime deletedAt = contract.getDeletedAt();
        LocalDateTime restorableUntil = deletedAt.plusDays(trashRetentionDays);
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), restorableUntil));
        // Show the status the contract had BEFORE trashing (not CANCELLED).
        String previousStatus = contract.getStatusBeforeDelete() != null
                ? contract.getStatusBeforeDelete().name()
                : (contract.getStatus() != null ? contract.getStatus().name() : null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", contract.getId());
        item.put("contractNumber", contract.getContractNumber());
        item.put("clientFullName", contract.getClientFullName());
        item.put("vehicleBrand", contract.getVehicleBrand());
        item.put("vehicleModel", contract.getVehicleModel());
        item.put("vehicleRegistration", contract.getVehicleRegistration());
        item.put("startDate", contract.getStartDate());
        item.put("endDate", contract.getEndDate());
        item.put("previousContractStatus", previousStatus);
        item.put("deletedAt", deletedAt);
        item.put("deletedBy", contract.getDeletedBy());
        item.put("restorableUntil", restorableUntil);
        item.put("daysRemaining", daysRemaining);
        return item;
    }

    @Transactional
    public Map<String, Object> restoreContract(Long id) {
        return restoreContract(id, "NORMAL");
    }

    @Transactional
    public Map<String, Object> restoreContract(Long id, String mode) {
        if (mode == null || mode.isBlank()) mode = "NORMAL";
        Long tenantId = TenantContext.getCurrentTenantId();
        Contract contract = contractRepository.findDeletedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Trashed contract not found with id: " + id));

        LocalDateTime deletedAt = contract.getDeletedAt();
        LocalDateTime restorableUntil = deletedAt != null ? deletedAt.plusDays(trashRetentionDays) : null;
        if (restorableUntil == null || LocalDateTime.now().isAfter(restorableUntil)) {
            throw new IllegalStateException(
                    "This contract's " + trashRetentionDays
                            + "-day restore window has expired and it can no longer be restored.");
        }

        Long vehicleId = contract.getVehicle() != null ? contract.getVehicle().getId() : null;
        log.info("[CONTRACT_RESTORE_ATTEMPT] contractId={} tenantId={} mode={} vehicleId={} startDate={} endDate={} deletedAt={}",
                id, tenantId, mode, vehicleId, contract.getStartDate(), contract.getEndDate(), deletedAt);

        boolean isDraftOnly = "DRAFT_ONLY".equalsIgnoreCase(mode);

        if (!isDraftOnly && vehicleId != null && contract.getStartDate() != null && contract.getEndDate() != null) {
            java.time.LocalTime pickupTime = contract.getPickupTime() != null
                    ? contract.getPickupTime() : java.time.LocalTime.of(9, 0);
            java.time.LocalTime returnTime = contract.getReturnTime() != null
                    ? contract.getReturnTime() : java.time.LocalTime.of(18, 0);
            // Exclude the contract's own linked reservation and the contract itself
            // to avoid false-positive conflicts on restore.
            Long excludeReservationId = contract.getReservation() != null ? contract.getReservation().getId() : null;
            boolean available = availabilityService.isVehicleAvailable(
                    vehicleId, contract.getStartDate(), pickupTime,
                    contract.getEndDate(), returnTime, excludeReservationId, id);
            if (!available) {
                AvailabilityService.ConflictDetail detail = availabilityService.findConflictDetail(
                        vehicleId, contract.getStartDate(), pickupTime,
                        contract.getEndDate(), returnTime, excludeReservationId, id);
                String conflictSource = detail != null ? detail.conflictSource() : "RESERVATION_OR_CONTRACT";
                Long conflictId = detail != null ? detail.conflictId() : null;
                java.time.LocalDate conflictStartDate = detail != null ? detail.conflictStartDate() : null;
                java.time.LocalDate conflictEndDate = detail != null ? detail.conflictEndDate() : null;
                String conflictNumber = detail != null ? detail.conflictNumber() : null;
                String conflictStatus = detail != null ? detail.conflictStatus() : null;
                log.warn("[CONTRACT_RESTORE_CONFLICT] contractId={} vehicleId={} conflictSource={} conflictId={} conflictNumber={} conflictStatus={} conflictStartDate={} conflictEndDate={}",
                        id, vehicleId, conflictSource, conflictId, conflictNumber, conflictStatus, conflictStartDate, conflictEndDate);
                throw new com.carrental.exception.VehicleConflictException(
                        "Cannot restore: this vehicle is already booked during these dates.",
                        "RESTORE_VEHICLE_CONFLICT", "vehicleId",
                        vehicleId, conflictSource != null ? conflictSource : "RESERVATION_OR_CONTRACT",
                        contract.getStartDate(), contract.getEndDate(),
                        conflictSource, conflictId, conflictStartDate, conflictEndDate,
                        conflictNumber, conflictStatus);
            }
        }

        // Determine what status the contract should have after restore.
        // For DRAFT_ONLY: always DRAFT (no availability side-effects).
        // For NORMAL: use statusBeforeDelete if available; fall back to the
        //             current contract.status (which we no longer change to
        //             CANCELLED on delete, so it may still be the real value).
        ContractStatus restoredStatus;
        if (isDraftOnly) {
            restoredStatus = ContractStatus.DRAFT;
        } else if (contract.getStatusBeforeDelete() != null) {
            restoredStatus = contract.getStatusBeforeDelete();
        } else {
            // statusBeforeDelete is null for contracts trashed before V3 migration.
            // Use the current status if it's not CANCELLED, else fall back.
            restoredStatus = (contract.getStatus() != null && contract.getStatus() != ContractStatus.CANCELLED)
                    ? contract.getStatus() : ContractStatus.WAITING_SIGNATURE;
        }

        contract.setStatus(restoredStatus);
        contract.setStatusBeforeDelete(null);
        contract.setDeleted(false);
        contract.setDeletedAt(null);
        contract.setDeletedBy(null);

        // Restore the linked reservation status.
        // DRAFT_ONLY: leave the reservation as-is (don't block vehicle).
        // NORMAL + previousReservationStatus set: restore to the status saved when it was cancelled
        //   during delete (old code path — backward compat for existing trashed rows).
        // NORMAL + previousReservationStatus null: reservation was never changed during delete
        //   (new behavior), so leave it in its current state — no restoration needed.
        String restoredReservationStatus = null;
        Reservation linkedReservation = contract.getReservation();
        if (!isDraftOnly && linkedReservation != null) {
            String prev = contract.getPreviousReservationStatus();
            if (prev != null && !prev.isBlank()) {
                try {
                    ReservationStatus targetReservationStatus = ReservationStatus.valueOf(prev);
                    linkedReservation.setStatus(targetReservationStatus);
                    reservationRepository.save(linkedReservation);
                    restoredReservationStatus = targetReservationStatus.name();
                    log.info("[CONTRACT_RESTORE] restored reservation reservationId={} previousStatus={}",
                            linkedReservation.getId(), restoredReservationStatus);
                } catch (IllegalArgumentException ignored) {
                    log.warn("[CONTRACT_RESTORE] invalid previousReservationStatus={} for reservationId={} — leaving reservation as-is",
                            prev, linkedReservation.getId());
                }
            }
            // If previousReservationStatus is null: the reservation was never changed during
            // delete (current behavior), so no restoration is needed — leave it as-is.
        }
        contract.setPreviousReservationStatus(null);

        // Update vehicle status to reflect the restored contract.
        if (!isDraftOnly && vehicleId != null) {
            vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
                VehicleStatus targetVehicleStatus;
                switch (restoredStatus) {
                    case ACTIVE -> targetVehicleStatus = VehicleStatus.RENTED;
                    case SIGNED, WAITING_SIGNATURE, WAITING_CLIENT_SIGNATURE,
                         PENDING_SIGNATURE, PARTIALLY_SIGNED, PAID -> targetVehicleStatus = VehicleStatus.RESERVED;
                    default -> targetVehicleStatus = null; // DRAFT / COMPLETED / CANCELLED: don't force vehicle status
                }
                if (targetVehicleStatus != null) {
                    log.info("[CONTRACT_RESTORE] vehicleId={} status {} → {}", vehicleId, vehicle.getStatut(), targetVehicleStatus);
                    vehicle.setStatut(targetVehicleStatus);
                    vehicleRepository.save(vehicle);
                }
            });
        }

        Contract saved = contractRepository.save(contract);
        String auditNote = isDraftOnly ? "Contract restored as draft (no availability check)" : "Contract restored from trash";
        logAudit(saved, "RESTORE", auditNote, null, restoredStatus.name());
        log.info("[CONTRACT_ACTION] action=RESTORE_{} contractId={} tenantId={} afterStatus={} deletedBefore=true deletedAfter=false reservationStatus={}",
                mode, id, tenantId, restoredStatus, restoredReservationStatus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("contractNumber", saved.getContractNumber());
        result.put("contractStatus", restoredStatus.name());
        result.put("previousContractStatus", contract.getStatusBeforeDelete() != null ? contract.getStatusBeforeDelete().name() : restoredStatus.name());
        result.put("deleted", false);
        result.put("restoredAsDraft", isDraftOnly);
        result.put("mode", mode);
        if (restoredReservationStatus != null) result.put("reservationStatus", restoredReservationStatus);
        return result;
    }

    // ── CANCEL (business action, does not trash) ─────────────────────────────

    @Transactional
    public Map<String, Object> cancelContract(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Contract contract = fetchContractInTenant(id);
        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw new IllegalStateException("Completed contracts cannot be cancelled");
        }
        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw new IllegalStateException("Contract is already cancelled");
        }
        ContractStatus beforeStatus = contract.getStatus();
        contract.setStatus(ContractStatus.CANCELLED);

        // Cancel the linked reservation so it no longer blocks availability.
        Reservation linkedReservation = contract.getReservation();
        if (linkedReservation != null && linkedReservation.getStatus() != ReservationStatus.CANCELLED) {
            linkedReservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(linkedReservation);
        }

        Long vehicleId = contract.getVehicle() != null ? contract.getVehicle().getId() : null;
        releaseVehicleAfterContractSafely(vehicleId, tenantId);

        Contract saved = contractRepository.save(contract);
        logAudit(saved, "CANCEL", "Contract cancelled by user", beforeStatus.name(), ContractStatus.CANCELLED.name());
        log.info("[CONTRACT_ACTION] action=CANCEL contractId={} contractNumber={} tenantId={} beforeStatus={} afterStatus=CANCELLED deletedBefore=false deletedAfter=false",
                id, saved.getContractNumber(), tenantId, beforeStatus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("contractNumber", saved.getContractNumber());
        result.put("contractStatus", ContractStatus.CANCELLED.name());
        result.put("deleted", false);
        return result;
    }

    // ── SIGNATURE WORKFLOW ───────────────────────────────────────────────────

    @Transactional
    public ContractResponse signContract(Long id, ContractSignatureRequest request) {
        Contract contract = fetchContractInTenant(id);
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("You do not have permission to sign this contract.");
        }
        ContractSignatureRequest.SignerType signerType = request.getResolvedSignerType();
        if (signerType == null) {
            throw new IllegalArgumentException("Signature type is required.");
        }
        if (contract.getTenant() == null) {
            throw new ResourceNotFoundException("Agency not found for this contract.");
        }
        if (contract.getClient() == null && !StringUtils.hasText(contract.getClientFullName())) {
            throw new ResourceNotFoundException("Client not found for this contract.");
        }
        if (contract.getVehicle() == null && !StringUtils.hasText(contract.getVehicleRegistration())) {
            throw new ResourceNotFoundException("Vehicle not found for this contract.");
        }

        // ── Enforce signature order: Agency MUST sign first ────────────────────
        if (signerType == ContractSignatureRequest.SignerType.CLIENT) {
            if (contract.getOwnerSignature() == null || contract.getOwnerSignature().isEmpty()) {
                throw new IllegalStateException("Agency must sign the contract before the client can sign.");
            }
        }

        if (signerType == ContractSignatureRequest.SignerType.OWNER) {
            if (StringUtils.hasText(contract.getOwnerSignature())) {
                boolean statusChanged = repairSignedStatus(contract);
                Contract saved = statusChanged ? contractRepository.save(contract) : contract;
                log.info("Contract [id={}] agency signature already existed in tenant [{}]", id, tenantId);
                return ContractResponse.from(saved);
            }
            // Auto-apply agency signature from tenant settings
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            String agencySig = tenant.getAgencySignature();
            if (agencySig != null && !agencySig.isEmpty()) {
                contract.setOwnerSignature(agencySig);
            } else {
                contract.setOwnerSignature(request.getSignatureData());
            }
            if (!StringUtils.hasText(contract.getOwnerSignature())) {
                throw new IllegalArgumentException("Agency signature is required.");
            }
            contract.setOwnerSignedAt(LocalDateTime.now());
            contract.setOwnerSignedIp(request.getIpAddress());
            contract.setOwnerUserAgent(StringUtils.hasText(request.getDeviceInfo())
                    ? request.getDeviceInfo() : request.getUserAgent());

            // Snapshot agency branding at signing time — freezes logo, stamp, and T&C
            // so future PDF regenerations use the branding from this moment.
            if (contract.getBrandingLogoUrl() == null) {
                contract.setBrandingLogoUrl(tenant.getLogoUrl());
            }
            if (contract.getBrandingStampUrl() == null) {
                contract.setBrandingStampUrl(tenant.getAgencyStampUrl());
            }
            if (contract.getBrandingTermsSnapshot() == null
                    && tenant.getTermsAndConditions() != null
                    && !tenant.getTermsAndConditions().isBlank()) {
                contract.setBrandingTermsSnapshot(tenant.getTermsAndConditions());
            }
            log.debug("[CONTRACT_BRANDING_DEBUG] contractId={} brandingLogoUrl={} brandingStampUrl={} brandingTermsSnapshot={}",
                    contract.getId(),
                    contract.getBrandingLogoUrl() != null ? "SET" : "null",
                    contract.getBrandingStampUrl() != null ? "SET" : "null",
                    contract.getBrandingTermsSnapshot() != null ? "SET(len=" + contract.getBrandingTermsSnapshot().length() + ")" : "null");

            try {
                notificationService.createNotification(
                        "Contract Signed by Agency",
                        "Contract " + contract.getContractNumber() + " has been signed by the agency.",
                        Notification.NotificationType.CONTRACT_SIGNED_AGENCY,
                        contract.getId(), tenantId);
            } catch (Exception e) {
                log.warn("Contract agency signature saved but notification failed [contractId={}]", contract.getId(), e);
            }

        } else if (signerType == ContractSignatureRequest.SignerType.CLIENT) {
            if (StringUtils.hasText(contract.getClientSignature())) {
                boolean statusChanged = repairSignedStatus(contract);
                Contract saved = statusChanged ? contractRepository.save(contract) : contract;
                log.info("Contract [id={}] client signature already existed in tenant [{}]", id, tenantId);
                return ContractResponse.from(saved);
            }
            if (!StringUtils.hasText(request.getSignatureData())) {
                throw new IllegalArgumentException("Client signature is required.");
            }
            contract.setClientSignature(request.getSignatureData());
            contract.setClientSignedAt(LocalDateTime.now());
            contract.setClientSignedIp(request.getIpAddress());
            contract.setClientUserAgent(StringUtils.hasText(request.getDeviceInfo())
                    ? request.getDeviceInfo() : request.getUserAgent());
        } else if (signerType == ContractSignatureRequest.SignerType.EMPLOYEE) {
            if (!StringUtils.hasText(request.getSignatureData())) {
                throw new IllegalArgumentException("Employee signature is required.");
            }
            contract.setEmployeeSignature(request.getSignatureData());
        }

        if (request.getTermsAccepted() != null && request.getTermsAccepted()) {
            contract.setTermsAccepted(true);
            contract.setTermsAcceptedAt(LocalDateTime.now());
        }

        contract.setSignedAt(LocalDateTime.now());
        updateStatusAfterSigning(contract);

        Contract saved = contractRepository.save(contract);
        logAudit(saved, "SIGN", signerType + " signed contract", null, null);

        // ── Regenerate and save PDF after any signature ────────────────────────
        try {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            com.carrental.entity.Deposit deposit = depositRepository.findByContractId(saved.getId()).orElse(null);
            byte[] pdf = pdfService.generateContractPdf(saved, tenant, deposit);
            String pdfUrl = pdfService.saveContractPdf(saved, pdf);
            saved.setPdfUrl(pdfUrl);
            saved = contractRepository.save(saved);
            log.info("PDF regenerated and saved for contract [id={}] after {} signature", saved.getId(), signerType);
        } catch (Exception e) {
            log.error("Failed to regenerate PDF after signing contract [id={}]", saved.getId(), e);
        }

        log.info("Contract [id={}] signed by {} in tenant [{}]", id, signerType, tenantId);
        return ContractResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public boolean isSignerAlreadySigned(Long id, ContractSignatureRequest.SignerType signerType) {
        Contract contract = fetchContractInTenant(id);
        if (signerType == ContractSignatureRequest.SignerType.OWNER) {
            return StringUtils.hasText(contract.getOwnerSignature());
        }
        if (signerType == ContractSignatureRequest.SignerType.CLIENT) {
            return StringUtils.hasText(contract.getClientSignature());
        }
        if (signerType == ContractSignatureRequest.SignerType.EMPLOYEE) {
            return StringUtils.hasText(contract.getEmployeeSignature());
        }
        return false;
    }

    @Transactional
    public String generateQrToken(Long id) {
        Contract contract = fetchContractInTenant(id);
        Long tenantId = TenantContext.getCurrentTenantId();

        if (StringUtils.hasText(contract.getQrToken())) {
            return contract.getQrToken();
        }

        // ── Enforce: agency must sign before QR generation ─────────────────────
        if (contract.getOwnerSignature() == null || contract.getOwnerSignature().isEmpty()) {
            throw new IllegalArgumentException("Agency signature is required before generating client QR code");
        }

        if (contract.getTenant() == null) {
            throw new ResourceNotFoundException("Agency not found for this contract.");
        }
        if (contract.getClient() == null && !StringUtils.hasText(contract.getClientFullName())) {
            throw new ResourceNotFoundException("Client not found for this contract.");
        }
        if (contract.getVehicle() == null && !StringUtils.hasText(contract.getVehicleRegistration())) {
            throw new ResourceNotFoundException("Vehicle not found for this contract.");
        }

        String token = generateSecureToken();
        contract.setQrToken(token);
        contract.setPublicSigningUrl(buildPublicSigningUrl(id, token));
        Contract saved = contractRepository.save(contract);

        logAudit(saved, "QR_GENERATED", "QR token generated for contract", null, null);
        log.info("Generated QR token for contract [id={}]", id);

        try {
            notificationService.createNotification(
                    "QR Code Generated",
                    "QR code for contract " + saved.getContractNumber() + " is ready to share with the client.",
                    Notification.NotificationType.QR_GENERATED,
                    saved.getId(), tenantId);
        } catch (Exception e) {
            log.warn("QR token generated but notification failed for contract [id={}]", saved.getId(), e);
        }

        // Send email to client if they have one
        try {
            byte[] qrPng = qrCodeService.generatePng(saved.getPublicSigningUrl(), 400);
            emailService.sendContractReadyEmail(
                    saved.getClientEmail(),
                    saved.getClientFullName(),
                    saved.getContractNumber(),
                    saved.getPublicSigningUrl(),
                    tenantRepository.findById(tenantId).map(Tenant::getName).orElse("Your Agency"),
                    qrPng);
        } catch (Exception e) {
            log.warn("QR token generated but contract-ready email failed for contract [id={}]", saved.getId(), e);
        }

        return token;
    }

    @Transactional(readOnly = true)
    public boolean hasQrCode(Long id) {
        Contract contract = fetchContractInTenant(id);
        return StringUtils.hasText(contract.getQrToken());
    }

    @Transactional
    public ContractResponse finalizeContract(Long id) {
        Contract contract = fetchContractInTenant(id);
        if (!hasRequiredSignatures(contract)) {
            throw new IllegalStateException("Cannot finalize: required signatures missing");
        }
        if (inspectionService != null && inspectionService.missingBeforeDeliveryInspection(contract)) {
            notificationService.createNotification(
                    "Before-delivery inspection missing",
                    "Contract " + contract.getContractNumber() + " is being activated without completed vehicle handover media.",
                    Notification.NotificationType.WARNING,
                    contract.getId(),
                    contract.getTenant().getId());
        }
        contract.setStatus(ContractStatus.ACTIVE);
        if (contract.getVehicle() != null) {
            contract.getVehicle().setStatut(VehicleStatus.RENTED);
            vehicleRepository.save(contract.getVehicle());
        }
        Contract saved = contractRepository.save(contract);
        logAudit(saved, "FINALIZE", "Contract finalized and activated", null, null);
        log.info("Contract [id={}] finalized", id);
        return ContractResponse.from(saved);
    }

    @Transactional
    public ContractResponse markCompleted(Long id) {
        Contract contract = fetchContractInTenant(id);
        contract.setStatus(ContractStatus.COMPLETED);
        releaseVehicleAfterContract(contract);
        Contract saved = contractRepository.save(contract);
        logAudit(saved, "COMPLETE", "Contract marked as completed", null, null);
        return ContractResponse.from(saved);
    }

    /**
     * Process vehicle return: save end fuel/mileage/condition, release vehicle, complete contract.
     * Returns comparison info (fuel diff, mileage diff) for the frontend to display warnings.
     */
    @Transactional
    public Map<String, Object> processReturnInspection(Long id, com.carrental.dto.contract.ReturnInspectionRequest req) {
        Contract contract = fetchContractInTenant(id);

        String fuelStart = contract.getFuelLevelStart();
        Integer mileStart = contract.getMileageStart();

        // Save end condition on contract
        if (req.getFuelLevelEnd() != null) contract.setFuelLevelEnd(req.getFuelLevelEnd());
        if (req.getMileageEnd() != null) contract.setMileageEnd(req.getMileageEnd());
        if (req.getConditionEndNote() != null) contract.setConditionEndNote(req.getConditionEndNote());
        if (req.getDamageEndNote() != null) contract.setDamageEndNote(req.getDamageEndNote());
        if (req.getExtraFuelFee() != null) contract.setFuelCharges(req.getExtraFuelFee());
        if (req.getExtraMileageFee() != null) contract.setLateFees(req.getExtraMileageFee());
        if (req.getDamageFee() != null) {
            java.math.BigDecimal existing = contract.getReturnFees() != null ? contract.getReturnFees() : java.math.BigDecimal.ZERO;
            contract.setReturnFees(existing.add(req.getDamageFee()));
        }

        contract.setStatus(ContractStatus.COMPLETED);

        // Update vehicle condition fields
        Long vehicleId = contract.getVehicle() != null ? contract.getVehicle().getId() : null;
        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, contract.getTenant().getId()).orElse(null);
            if (vehicle != null) {
                if (req.getFuelLevelEnd() != null) vehicle.setFuelLevelCurrent(req.getFuelLevelEnd());
                if (req.getMileageEnd() != null) vehicle.setMileageCurrent(req.getMileageEnd());
                vehicle.setLastReturnedAt(LocalDateTime.now());
                vehicleRepository.save(vehicle);
            }
        }

        Contract saved = contractRepository.save(contract);
        // Recalculate from real blockers (not just a raw reservation-exists check) so an
        // expired reservation or active maintenance can never leave the vehicle stuck.
        // Must run after the contract's COMPLETED status is persisted, otherwise the
        // now-stale in-memory status would still count as an active/blocking contract.
        if (vehicleId != null) {
            vehicleStatusSyncService.recalculateVehicleStatus(vehicleId, contract.getTenant().getId());
        }
        logAudit(saved, "RETURN_INSPECTION", "Vehicle returned and inspected", null, null);

        // Build comparison result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId", saved.getId());
        result.put("contractNumber", saved.getContractNumber());
        result.put("status", saved.getStatus());
        result.put("fuelLevelStart", fuelStart);
        result.put("fuelLevelEnd", req.getFuelLevelEnd());
        result.put("mileageStart", mileStart);
        result.put("mileageEnd", req.getMileageEnd());
        if (mileStart != null && req.getMileageEnd() != null) {
            result.put("mileageDriven", req.getMileageEnd() - mileStart);
        }
        result.put("fuelWarning", fuelLevelLower(fuelStart, req.getFuelLevelEnd()));
        result.put("extraFuelFee", req.getExtraFuelFee());
        result.put("extraMileageFee", req.getExtraMileageFee());
        result.put("damageFee", req.getDamageFee());
        return result;
    }

    private static final List<String> FUEL_ORDER = List.of("EMPTY", "QUARTER", "HALF", "THREE_QUARTERS", "FULL");

    private boolean fuelLevelLower(String start, String end) {
        if (start == null || end == null) return false;
        int si = FUEL_ORDER.indexOf(start.toUpperCase());
        int ei = FUEL_ORDER.indexOf(end.toUpperCase());
        return si >= 0 && ei >= 0 && ei < si;
    }

    @Transactional(readOnly = true)
    public byte[] generateContractPdf(Long id) {
        Contract contract = fetchContractInTenant(id);
        Tenant tenant = tenantRepository.findById(contract.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        com.carrental.entity.Deposit deposit = depositRepository.findByContractId(contract.getId()).orElse(null);
        return pdfService.generateContractPdf(contract, tenant, deposit);
    }

    /**
     * Public (unauthenticated) equivalent of {@link #generateContractPdf(Long)}.
     * That method loads the contract via {@link #fetchContractInTenant(Long)},
     * which reads {@code TenantContext.getCurrentTenantId()} — always null for
     * a public request, since {@code JwtAuthenticationFilter.shouldNotFilter}
     * skips the whole "/api/public/**" prefix, so the tenant-scoped lookup
     * always threw ResourceNotFoundException even for a perfectly valid
     * contract/token pair (surfaced to the client as a false "Invalid Link").
     * Safe to bypass the tenant filter here because the caller
     * (ContractController#downloadPublicContractPdf) already verified the
     * qrToken belongs to this exact contractId before calling this method —
     * that check *is* the tenant/access boundary for the public PDF flow.
     */
    @Transactional(readOnly = true)
    public byte[] generateContractPdfPublic(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        Tenant tenant = tenantRepository.findById(contract.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        com.carrental.entity.Deposit deposit = depositRepository.findByContractId(contract.getId()).orElse(null);
        return pdfService.generateContractPdf(contract, tenant, deposit);
    }

    /**
     * Forces a fresh PDF render from the agency's *current* settings and
     * overwrites the stored file/pdfUrl — used so a signed contract's frozen
     * PDF snapshot (captured at signing time) can be refreshed after the
     * agency updates its branding (logo, name, terms, etc.), instead of the
     * download/preview endpoints silently keeping serving the stale file.
     */
    @Transactional
    public ContractResponse regenerateContractPdf(Long id) {
        Contract contract = fetchContractInTenant(id);
        Tenant tenant = tenantRepository.findById(contract.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        com.carrental.entity.Deposit deposit = depositRepository.findByContractId(contract.getId()).orElse(null);
        byte[] pdf = pdfService.generateContractPdf(contract, tenant, deposit);
        String pdfUrl = pdfService.saveContractPdf(contract, pdf);
        contract.setPdfUrl(pdfUrl);
        contract.setUpdatedAt(LocalDateTime.now());
        Contract saved = contractRepository.save(contract);
        log.info("[PDF_REGENERATE] contractId={} tenantId={} regenerated=true", id, tenant.getId());
        return ContractResponse.from(saved);
    }

    // ── PUBLIC SIGNING ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicContractResponse getPublicContract(String qrToken) {
        Contract contract = contractRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        Tenant tenant = contract.getTenant();

        return PublicContractResponse.builder()
                .contractNumber(contract.getContractNumber())
                .status(contract.getStatus())
                .contractType(contract.getContractType())
                .contractLanguage(contract.getContractLanguage())
                .contractDuration(contract.getContractDuration())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .pickupDate(contract.getPickupDate())
                .returnDate(contract.getReturnDate())
                .pickupLocation(contract.getPickupLocation())
                .returnLocation(contract.getReturnLocation())
                .clientFirstName(contract.getClientFirstName())
                .clientLastName(contract.getClientLastName())
                .clientFullName(contract.getClientFullName())
                .clientNationality(contract.getClientNationality())
                .clientCin(contract.getClientCin())
                .clientPassportNumber(contract.getClientPassportNumber())
                .clientDriverLicense(contract.getClientDriverLicense())
                .clientAddress(contract.getClientAddress())
                .clientCity(contract.getClientCity())
                .clientCountry(contract.getClientCountry())
                .clientPhone(contract.getClientPhone())
                .clientEmail(contract.getClientEmail())
                .emergencyContactName(contract.getEmergencyContactName())
                .emergencyContactPhone(contract.getEmergencyContactPhone())
                .vehicleBrand(contract.getVehicleBrand())
                .vehicleModel(contract.getVehicleModel())
                .vehicleCategory(contract.getVehicleCategory())
                .vehicleYear(contract.getVehicleYear())
                .vehicleColor(contract.getVehicleColor())
                .vehicleRegistration(contract.getVehicleRegistration())
                .vehicleVin(contract.getVehicleVin())
                .vehicleTransmission(contract.getVehicleTransmission())
                .pickupAgency(contract.getPickupAgency())
                .returnAgency(contract.getReturnAgency())
                .rentalDays(contract.getRentalDays())
                .allowedMileage(contract.getAllowedMileage())
                .extraMileageCost(contract.getExtraMileageCost())
                .deliveryFees(contract.getDeliveryFees())
                .returnFees(contract.getReturnFees())
                .lateFees(contract.getLateFees())
                .cleaningFees(contract.getCleaningFees())
                .fuelCharges(contract.getFuelCharges())
                .totalPrice(contract.getTotalPrice())
                .dailyPrice(contract.getDailyPrice())
                .depositAmount(contract.getDepositAmount())
                .depositCurrency(contract.getDepositCurrency() != null ? contract.getDepositCurrency() : "MAD")
                .depositStatus(contract.getDepositStatus())
                .paidAmount(contract.getPaidAmount())
                .remainingAmount(contract.getRemainingAmount())
                .taxAmount(contract.getTaxAmount())
                .discountAmount(contract.getDiscountAmount())
                .paymentMethod(contract.getPaymentMethod())
                .fuelType(contract.getFuelType())
                .fuelLevelStart(contract.getFuelLevelStart())
                .fuelLevelEnd(contract.getFuelLevelEnd())
                .mileageStart(contract.getMileageStart())
                .mileageEnd(contract.getMileageEnd())
                .clientSigned(contract.getClientSignature() != null && !contract.getClientSignature().isEmpty())
                .ownerSigned(contract.getOwnerSignature() != null && !contract.getOwnerSignature().isEmpty())
                .employeeSigned(contract.getEmployeeSignature() != null && !contract.getEmployeeSignature().isEmpty())
                .termsAccepted(contract.getTermsAccepted())
                .signedAt(contract.getSignedAt())
                .ownerSignature(contract.getOwnerSignature())
                .clientSignature(contract.getClientSignature())
                .ownerSignedAt(contract.getOwnerSignedAt())
                .clientSignedAt(contract.getClientSignedAt())
                .agencyStampUrl(tenant.getAgencyStampUrl())
                // Deliberately NOT contract.getPdfUrl() — that field is
                // "/api/contracts/{id}/pdf-file", the AUTHENTICATED admin
                // endpoint (@PreAuthorize VIEW_CONTRACTS). A public/anonymous
                // visitor's browser would get 401/403 fetching it, which is
                // exactly the "Unable to generate contract PDF" bug this page
                // hit in production. Public responses must only ever expose
                // the public, token-scoped PDF endpoint.
                .pdfUrl(contract.getQrToken() != null
                        ? "/api/public/contracts/" + contract.getId() + "/" + contract.getQrToken() + "/pdf"
                        : null)
                .deposit(com.carrental.dto.deposit.DepositResponse.from(
                        depositRepository.findByContractId(contract.getId()).orElse(null)))
                .agencyName(tenant.getName())
                .agencyAddress(tenant.getAddress())
                .agencyCity(tenant.getCity())
                .agencyCountry(tenant.getCountry())
                .agencyPhone(tenant.getPhone())
                .agencyEmail(tenant.getEmail())
                .agencyLogo(tenant.getLogoUrl())
                .vehicleCondition(contract.getVehicleCondition() != null
                        ? VehicleConditionDto.builder()
                            .id(contract.getVehicleCondition().getId())
                            .frontDamage(contract.getVehicleCondition().getFrontDamage())
                            .rearDamage(contract.getVehicleCondition().getRearDamage())
                            .leftSideDamage(contract.getVehicleCondition().getLeftSideDamage())
                            .rightSideDamage(contract.getVehicleCondition().getRightSideDamage())
                            .windshieldDamage(contract.getVehicleCondition().getWindshieldDamage())
                            .interiorDamage(contract.getVehicleCondition().getInteriorDamage())
                            .tireCondition(contract.getVehicleCondition().getTireCondition())
                            .scratchDescription(contract.getVehicleCondition().getScratchDescription())
                            .dentDescription(contract.getVehicleCondition().getDentDescription())
                            .generalNotes(contract.getVehicleCondition().getGeneralNotes())
                            .build()
                        : null)
                .terms(resolveTerms(tenant, contract))
                .additionalDrivers(contract.getAdditionalDrivers() != null
                        ? contract.getAdditionalDrivers().stream()
                            .map(d -> AdditionalDriverDto.builder()
                                .id(d.getId())
                                .fullName(d.getFullName())
                                .cin(d.getCin())
                                .driverLicenseNumber(d.getDriverLicenseNumber())
                                .phone(d.getPhone())
                                .build())
                            .collect(Collectors.toList())
                        : List.of())
                .build();
    }

    @Transactional
    public PublicContractResponse signPublicContract(String qrToken, ContractSignatureRequest request) {
        Contract contract = contractRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        // ── Enforce: agency must have signed first ─────────────────────────────
        if (contract.getOwnerSignature() == null || contract.getOwnerSignature().isEmpty()) {
            throw new IllegalStateException("This contract is not yet ready for client signature.");
        }

        // ── Prevent re-signing an already fully signed contract ────────────────
        boolean alreadyFullySigned = contract.getClientSignature() != null
                && !contract.getClientSignature().isEmpty()
                && contract.getOwnerSignature() != null
                && !contract.getOwnerSignature().isEmpty();
        if (alreadyFullySigned) {
            throw new IllegalStateException("This contract has already been fully signed.");
        }

        contract.setClientSignature(request.getSignatureData());
        contract.setClientSignedAt(LocalDateTime.now());
        contract.setClientSignedIp(request.getIpAddress());
        contract.setClientUserAgent(request.getUserAgent());
        contract.setSignedAt(LocalDateTime.now());

        if (request.getTermsAccepted() != null && request.getTermsAccepted()) {
            contract.setTermsAccepted(true);
            contract.setTermsAcceptedAt(LocalDateTime.now());
        }

        updateStatusAfterSigning(contract);
        Contract saved = contractRepository.save(contract);

        logAudit(saved, "SIGN", "Client signed via QR", null, null);
        log.info("Contract [id={}] fully signed by client via QR", saved.getId());

        // ── Trigger notifications ──────────────────────────────────────────────
        Long tenantId = saved.getTenant().getId();
        try {
            notificationService.createNotification(
                    "Contract Fully Signed",
                    "Client signed contract " + saved.getContractNumber() + ". The contract is now fully signed.",
                    Notification.NotificationType.CONTRACT_FULLY_SIGNED,
                    saved.getId(), tenantId);
        } catch (Exception e) {
            log.warn("Public contract signature saved but notification failed [contractId={}]", saved.getId(), e);
        }

        // ── Broadcast real-time contract event for admin dashboard auto-refresh ─
        try {
            sseService.broadcastContractEvent(
                    tenantId, saved.getId(), saved.getContractNumber(), saved.getClientFullName(),
                    saved.getStatus() != null ? saved.getStatus().name() : "SIGNED",
                    "CLIENT_SIGNED");
        } catch (Exception e) {
            log.warn("Public contract signature saved but SSE contract_event failed [contractId={}]", saved.getId(), e);
        }

        // ── Auto-regenerate and save the final signed PDF BEFORE emailing the
        // client — the "Contract Signed" email attaches this PDF and links to
        // it, so the email must not go out ahead of PDF generation actually
        // succeeding. Failure here must never block the signature itself or
        // crash the request; it's recorded and the agency is notified so they
        // can retry (the download link still regenerates the PDF on demand,
        // so a transient failure here doesn't leave the link permanently dead).
        try {
            com.carrental.entity.Deposit deposit = depositRepository.findByContractId(saved.getId()).orElse(null);
            byte[] pdf = pdfService.generateContractPdf(saved, saved.getTenant(), deposit);
            String pdfUrl = pdfService.saveContractPdf(saved, pdf);
            saved.setPdfUrl(pdfUrl);
            saved = contractRepository.save(saved);
            log.info("PDF regenerated and saved for fully signed contract [id={}]", saved.getId());
            logAudit(saved, "PDF_GENERATED", "PDF auto-regenerated and saved after client signature", null, null);
        } catch (Exception e) {
            log.error("[CONTRACT_PDF_GENERATION_FAILED] Failed to regenerate PDF for contract [id={}]", saved.getId(), e);
            logAudit(saved, "PDF_GENERATION_FAILED", "Automatic PDF generation failed after client signature: " + e.getMessage(), null, null);
            try {
                notificationService.createNotification(
                        "Contract PDF generation failed",
                        "The signed PDF for contract " + saved.getContractNumber() + " could not be generated automatically. Use Resend Email to retry once resolved.",
                        Notification.NotificationType.WARNING,
                        saved.getId(), tenantId);
            } catch (Exception notifyEx) {
                log.warn("Failed to notify admin about PDF generation failure [contractId={}]", saved.getId(), notifyEx);
            }
        }

        // ── Send confirmation email to client ──────────────────────────────────
        try {
            platformEmailService.sendContractSignedEmail(saved.getId());
        } catch (Exception e) {
            log.warn("Public contract signature saved but email dispatch failed [contractId={}]", saved.getId(), e);
        }

        return getPublicContract(qrToken);
    }

    // ── PUBLIC SIGNING WITH CONTRACT ID + TOKEN ──────────────────────────────

    @Transactional(readOnly = true)
    public PublicContractResponse getPublicContract(Long contractId, String qrToken) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
        if (contract.getQrToken() == null || !contract.getQrToken().equals(qrToken)) {
            throw new ResourceNotFoundException("Contract not found");
        }
        // Reuse the existing builder by temporarily setting a matching token
        return getPublicContract(qrToken);
    }

    @Transactional
    public PublicContractResponse signPublicContract(Long contractId, String qrToken, ContractSignatureRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
        if (contract.getQrToken() == null || !contract.getQrToken().equals(qrToken)) {
            throw new ResourceNotFoundException("Contract not found");
        }
        return signPublicContract(qrToken, request);
    }

    // ── CREATE FROM RESERVATION ──────────────────────────────────────────────

    /** Result of {@link #createFromReservation}: the contract plus whether it already existed (idempotent replay). */
    public record FromReservationResult(ContractResponse contract, boolean alreadyExisted) {}

    @Transactional
    public FromReservationResult createFromReservation(Long reservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Long currentUserId = null;
        try {
            currentUserId = ((com.carrental.entity.User) org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal()).getId();
        } catch (Exception ignored) { /* system/anonymous context */ }
        log.debug("[CONTRACT_FROM_RESERVATION_DEBUG] endpointHit=true reservationId={} currentUserId={} tenantId={}",
                reservationId, currentUserId, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Reservation reservation = reservationRepository.findByIdAndTenantId(reservationId, tenantId).orElse(null);
        log.debug("[CONTRACT_FROM_RESERVATION_DEBUG] reservationId={} reservationFound={} reservationTenantId={}",
                reservationId, reservation != null, reservation != null ? reservation.getTenant().getId() : null);
        if (reservation == null) {
            throw new ResourceNotFoundException("Reservation not found with id: " + reservationId);
        }

        Client client = reservation.getClient();
        Vehicle vehicle = reservation.getVehicle();
        log.debug("[CONTRACT_FROM_RESERVATION_DEBUG] reservationId={} clientId={} clientFound={} vehicleId={} vehicleFound={} "
                        + "reservationStatus={} dateStart={} startTime={} dateEnd={} endTime={} totalPrice={} depositAmount={} "
                        + "paidAmount={} paymentStatus={} existingContractId={}",
                reservationId,
                client != null ? client.getId() : null, client != null,
                vehicle != null ? vehicle.getId() : null, vehicle != null,
                reservation.getStatus(), reservation.getDateStart(), reservation.getStartTime(),
                reservation.getDateEnd(), reservation.getEndTime(), reservation.getTotalPrice(),
                reservation.getDepositAmount(), reservation.getPaidAmount(), reservation.getPaymentStatus(),
                reservation.getContract() != null ? reservation.getContract().getId() : null);

        if (reservation.getContract() != null) {
            log.debug("[CONTRACT_FROM_RESERVATION_DEBUG] reservationId={} result=ALREADY_EXISTS existingContractId={}",
                    reservationId, reservation.getContract().getId());
            return new FromReservationResult(ContractResponse.from(reservation.getContract()), true);
        }
        if (reservation.getStatus() == ReservationStatus.CONVERTED_TO_CONTRACT) {
            // The reservation is marked converted but the reservation→contract link
            // wasn't found above (e.g. the contract was later deleted) — still a
            // conflict, never silently create a second contract for it.
            throw new IllegalStateException("CONTRACT_ALREADY_EXISTS");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("INVALID_RESERVATION_STATUS_FOR_CONTRACT");
        }

        if (client == null) {
            throw new IllegalArgumentException("RESERVATION_CLIENT_MISSING");
        }
        if (vehicle == null) {
            throw new IllegalArgumentException("RESERVATION_VEHICLE_MISSING");
        }
        if (reservation.getDateStart() == null || reservation.getDateEnd() == null) {
            throw new IllegalArgumentException("INVALID_RESERVATION_PERIOD");
        }

        java.time.LocalTime startTime = reservation.getStartTime() != null ? reservation.getStartTime() : java.time.LocalTime.of(9, 0);
        java.time.LocalTime endTime = reservation.getEndTime() != null ? reservation.getEndTime() : java.time.LocalTime.of(18, 0);

        long days = java.time.temporal.ChronoUnit.DAYS.between(reservation.getDateStart(), reservation.getDateEnd()) + 1;
        java.math.BigDecimal totalPrice = reservation.getTotalPrice() != null ? reservation.getTotalPrice() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal deposit = vehicle.getDepositAmount() != null ? vehicle.getDepositAmount() : java.math.BigDecimal.ZERO;

        String contractNumber = generateContractNumber();

        String[] marqueParts = vehicle.getMarque() != null ? vehicle.getMarque().split(" ", 2) : new String[]{"", ""};

        Contract contract = Contract.builder()
                .contractNumber(contractNumber)
                .status(ContractStatus.DRAFT)
                .reservation(reservation)
                .client(client)
                .vehicle(vehicle)
                .startDate(reservation.getDateStart())
                .endDate(reservation.getDateEnd())
                .pickupTime(startTime)
                .returnTime(endTime)
                .pickupLocation(reservation.getPickupLocation())
                .returnLocation(reservation.getReturnLocation())
                .clientFirstName(client.getName() != null ? client.getName().split(" ", 2)[0] : null)
                .clientLastName(client.getName() != null && client.getName().contains(" ")
                        ? client.getName().substring(client.getName().indexOf(" ") + 1) : null)
                .clientFullName(client.getName())
                .clientName(client.getName())
                .clientNationality(client.getNationality())
                .clientGender(client.getGender())
                .clientBirthDate(client.getBirthDate())
                .clientCin(client.getCin())
                .clientPassportNumber(client.getPassportNumber())
                .clientDriverLicense(client.getDrivingLicense())
                .clientDriverLicenseIssue(client.getDrivingLicenseIssue())
                .clientDriverLicenseExpiry(client.getDrivingLicenseExpiry())
                .clientAddress(client.getAddress())
                .clientCity(client.getCity())
                .clientCountry(client.getCountry())
                .clientPostalCode(client.getPostalCode())
                .clientPhone(client.getPhone())
                .clientSecondaryPhone(client.getSecondaryPhone())
                .clientEmail(client.getEmail())
                .emergencyContactName(client.getEmergencyContactName())
                .emergencyContactPhone(client.getEmergencyContactPhone())
                .vehicleBrand(marqueParts[0])
                .vehicleModel(marqueParts.length > 1 ? marqueParts[1] : "")
                .vehicleCategory(vehicle.getCategory())
                .vehicleYear(vehicle.getYear())
                .vehicleColor(vehicle.getColor())
                .vehicleRegistration(vehicle.getPlate())
                .vehicleTransmission(vehicle.getTransmission())
                .fuelType(vehicle.getFuel())
                .insuranceExpiration(vehicle.getInsuranceExpiration())
                .technicalInspectionExpiration(vehicle.getTechnicalInspectionExpiration())
                .rentalDays((int) days)
                .totalPrice(totalPrice)
                .dailyPrice(vehicle.getPrixJour())
                .depositAmount(deposit)
                .paidAmount(java.math.BigDecimal.ZERO)
                .remainingAmount(totalPrice)
                .paymentStatus("pending")
                .fuelLevelStart("Full")
                .mileageStart(0)
                .notes(reservation.getNotes())
                .tenant(tenant)
                .termsAccepted(false)
                .build();

        Contract saved = contractRepository.save(contract);
        reservation.setContract(saved);
        reservation.setStatus(ReservationStatus.CONVERTED_TO_CONTRACT);
        reservationRepository.save(reservation);
        vehicle.setStatut(VehicleStatus.RENTED);
        vehicleRepository.save(vehicle);

        // Link existing deposit from reservation to the new contract
        try {
            depositService.linkDepositToContract(reservationId, saved);
        } catch (Exception e) {
            log.warn("Failed to link deposit to contract [id={}] from reservation [id={}]", saved.getId(), reservationId, e);
        }

        logAudit(saved, "CREATE", "Contract auto-generated from reservation " + reservationId, null, null);
        log.debug("[CONTRACT_FROM_RESERVATION_DEBUG] reservationId={} result=CREATED contractId={} contractNumber={}",
                reservationId, saved.getId(), saved.getContractNumber());
        return new FromReservationResult(ContractResponse.from(saved), false);
    }

    // ── AUTO GENERATION ──────────────────────────────────────────────────────

    @Transactional
    public String generateContractNumber() {
        String prefix = "CTR";
        String year = String.valueOf(java.time.Year.now().getValue());
        long count = contractRepository.count() + 1;
        String number = String.format("%s-%s-%05d", prefix, year, count);

        // Ensure uniqueness against the real table contents (including
        // soft-deleted rows) — see existsByContractNumberIncludingDeleted javadoc.
        while (contractRepository.existsByContractNumberIncludingDeleted(number)) {
            count++;
            number = String.format("%s-%s-%05d", prefix, year, count);
        }
        return number;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Contract fetchContractInTenant(Long contractId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return contractRepository.findByIdAndTenantId(contractId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + contractId));
    }

    private void validateRentalPeriod(java.time.LocalDate startDate, java.time.LocalTime startTime,
                                      java.time.LocalDate endDate, java.time.LocalTime endTime) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Rental start and end dates are required.");
        }
        if (startDate.isAfter(endDate)
                || (startDate.equals(endDate) && startTime != null && endTime != null
                && !startTime.isBefore(endTime))) {
            throw new IllegalArgumentException("Rental end must be after rental start.");
        }
    }

    private void assertVehicleCanStartDirectRental(Vehicle vehicle) {
        if (EnumSet.of(VehicleStatus.OUT_OF_SERVICE, VehicleStatus.SOLD, VehicleStatus.ARCHIVED)
                .contains(vehicle.getStatut())) {
            throw new com.carrental.exception.VehicleConflictException(
                    "Vehicle is not available for rental in its current status (" + vehicle.getStatut() + ").",
                    "VEHICLE_INACTIVE", "vehicleId", vehicle.getId(), "VEHICLE_STATUS");
        }
    }

    private void releaseVehicleAfterContract(Contract contract) {
        // contract.getVehicle() is never dereferenced beyond getId() (safe —
        // resolved from the proxy's own identifier, no DB hit), so this never
        // throws EntityNotFoundException for a vehicle that was since
        // soft-deleted and is now invisible to every normal query.
        Long vehicleId = contract.getVehicle() != null ? contract.getVehicle().getId() : null;
        releaseVehicleAfterContractSafely(vehicleId, contract.getTenant().getId());
    }

    /**
     * Resets a vehicle's status once its contract ends/is cancelled. Looks
     * the vehicle up by id with a real query (which simply returns empty for
     * a missing/soft-deleted vehicle) instead of touching the contract's lazy
     * {@code Vehicle} proxy, which throws {@code EntityNotFoundException} if
     * the row is gone — that crash is exactly what broke contract delete for
     * contracts whose linked vehicle had since been soft-deleted.
     */
    private void releaseVehicleAfterContractSafely(Long vehicleId, Long tenantId) {
        if (vehicleId == null) {
            return;
        }
        if (!vehicleRepository.existsByIdAndTenantId(vehicleId, tenantId)) {
            log.debug("[CONTRACT_DELETE_DEBUG] vehicleId={} no longer exists — skipping vehicle status release", vehicleId);
            return;
        }
        // Recalculate from real blockers (maintenance, other contracts, non-expired
        // reservations) instead of a raw reservation-exists check — otherwise an
        // expired reservation or active maintenance work order would be ignored.
        vehicleStatusSyncService.recalculateVehicleStatus(vehicleId, tenantId);
    }

    private <T> void updateField(T value, java.util.function.Consumer<T> setter) {
        if (value != null) setter.accept(value);
    }

    private boolean hasRequiredSignatures(Contract contract) {
        boolean client = contract.getClientSignature() != null && !contract.getClientSignature().isEmpty();
        boolean owner = contract.getOwnerSignature() != null && !contract.getOwnerSignature().isEmpty();
        return client && owner;
    }

    private void updateStatusAfterSigning(Contract contract) {
        repairSignedStatus(contract);
    }

    private boolean repairSignedStatus(Contract contract) {
        if (contract == null || isTerminalContractStatus(contract.getStatus())) {
            return false;
        }
        ContractStatus previous = contract.getStatus();
        boolean client = StringUtils.hasText(contract.getClientSignature());
        boolean owner = StringUtils.hasText(contract.getOwnerSignature());

        if (client && owner) {
            contract.setStatus(ContractStatus.ACTIVE);
        } else if (client || owner) {
            contract.setStatus(ContractStatus.PENDING_SIGNATURE);
        } else if (contract.getStatus() == null) {
            contract.setStatus(ContractStatus.DRAFT);
        }
        return previous != contract.getStatus();
    }

    private boolean isTerminalContractStatus(ContractStatus status) {
        return status == ContractStatus.COMPLETED
                || status == ContractStatus.CANCELLED
                || status == ContractStatus.EXPIRED;
    }

    private String savedContractPath(Long contractId, String token) {
        return contractId + "/" + token;
    }

    private String buildPublicSigningUrl(Long contractId, String token) {
        String base = frontendUrl.replaceAll("/+$", "");
        return base + "/#/contract-sign/" + savedContractPath(contractId, token);
    }

    private void logAudit(Contract contract, String action, String description, String oldValue, String newValue) {
        try {
            ContractAuditLog log = ContractAuditLog.builder()
                    .action(action)
                    .description(description)
                    .performedBy(getCurrentUser())
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .contract(contract)
                    .build();
            contractAuditLogRepository.save(log);
        } catch (Exception e) {
            // Don't fail the main operation if audit logging fails
        }
    }

    private String getCurrentUser() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    private List<String> resolveTerms(Tenant tenant, Contract contract) {
        if (tenant.getTermsAndConditions() != null && !tenant.getTermsAndConditions().isBlank()) {
            return List.of(tenant.getTermsAndConditions().split("\\r?\\n"))
                    .stream()
                    .filter(t -> !t.trim().isEmpty())
                    .collect(Collectors.toList());
        }
        return List.of(
                "The vehicle must be returned with the same fuel level as received.",
                "Any damage to the vehicle during the rental period is the responsibility of the client.",
                "Late return will incur additional charges as per the company policy.",
                "The vehicle is insured for third-party liability only.",
                "Smoking is strictly prohibited inside the vehicle."
        );
    }

    private java.math.BigDecimal resolveDepositAmount(java.math.BigDecimal requested) {
        if (requested == null) return java.math.BigDecimal.ZERO;
        if (requested.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("INVALID_DEPOSIT_AMOUNT");
        }
        return requested;
    }

    private com.carrental.entity.DepositStatus resolveDepositStatus(
            com.carrental.entity.DepositStatus requested, java.math.BigDecimal depositAmount) {
        if (requested != null) return requested;
        boolean hasDeposit = depositAmount != null && depositAmount.compareTo(java.math.BigDecimal.ZERO) > 0;
        return hasDeposit ? com.carrental.entity.DepositStatus.PENDING : com.carrental.entity.DepositStatus.NOT_REQUIRED;
    }

    private String generateSecureToken() {
        try {
            String raw = UUID.randomUUID().toString() + System.currentTimeMillis();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        }
    }
}

