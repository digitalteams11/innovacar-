package com.carrental.service;

import com.carrental.dto.contract.*;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
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
    private final EmailService emailService;
    private final DepositService depositService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ── READ ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractResponse> getAllContracts() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return contractRepository.findAllByTenantId(tenantId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse getContractById(Long id) {
        Contract contract = fetchContractInTenant(id);
        ContractResponse response = ContractResponse.from(contract);
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

        ContractStatus status = request.getStatus() != null ? request.getStatus() : ContractStatus.DRAFT;

        // ── Resolve or auto-create client ──────────────────────────────────────
        Client client = null;
        if (request.getClientId() != null) {
            client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + request.getClientId()));
        } else if (StringUtils.hasText(request.getClientFullName()) || StringUtils.hasText(request.getClientPhone())) {
            // Auto-create a new client if manual data provided but no clientId
            String fullName = StringUtils.hasText(request.getClientFullName())
                    ? request.getClientFullName()
                    : (request.getClientFirstName() + " " + request.getClientLastName()).trim();
            client = clientRepository.save(Client.builder()
                    .name(fullName)
                    .email(request.getClientEmail())
                    .phone(request.getClientPhone())
                    .secondaryPhone(request.getClientSecondaryPhone())
                    .address(request.getClientAddress())
                    .city(request.getClientCity())
                    .country(request.getClientCountry())
                    .postalCode(request.getClientPostalCode())
                    .nationality(request.getClientNationality())
                    .gender(request.getClientGender())
                    .birthDate(request.getClientBirthDate())
                    .cin(request.getClientCin())
                    .passportNumber(request.getClientPassportNumber())
                    .drivingLicense(request.getClientDriverLicense())
                    .drivingLicenseIssue(request.getClientDriverLicenseIssue())
                    .drivingLicenseExpiry(request.getClientDriverLicenseExpiry())
                    .emergencyContactName(request.getEmergencyContactName())
                    .emergencyContactPhone(request.getEmergencyContactPhone())
                    .tenant(tenant)
                    .build());
            log.info("Auto-created client [id={}] from contract creation in tenant [{}]", client.getId(), tenantId);
        }

        // ── Resolve vehicle ────────────────────────────────────────────────────
        Vehicle vehicle = null;
        if (request.getVehicleId() != null) {
            vehicle = vehicleRepository.findByIdAndTenantId(request.getVehicleId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));
        }

        // ── Build contract with auto-populated snapshots ───────────────────────
        Contract.ContractBuilder builder = Contract.builder()
                .contractNumber(request.getContractNumber())
                .status(status)
                .contractType(request.getContractType())
                .contractLanguage(request.getContractLanguage())
                .contractDuration(request.getContractDuration())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .pickupDate(request.getPickupDate())
                .returnDate(request.getReturnDate())
                .pickupTime(request.getPickupTime())
                .returnTime(request.getReturnTime())
                .pickupLocation(request.getPickupLocation())
                .returnLocation(request.getReturnLocation())
                .totalPrice(request.getTotalPrice())
                .dailyPrice(request.getDailyPrice())
                .depositAmount(request.getDepositAmount())
                .paidAmount(request.getPaidAmount())
                .remainingAmount(request.getRemainingAmount())
                .taxAmount(request.getTaxAmount())
                .discountAmount(request.getDiscountAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(request.getPaymentStatus())
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
        builder.rentalDays(request.getRentalDays());
        builder.extraHours(request.getExtraHours());
        builder.allowedMileage(request.getAllowedMileage());
        builder.extraMileageCost(request.getExtraMileageCost());
        builder.returnFees(request.getReturnFees());
        builder.lateFees(request.getLateFees());
        builder.cleaningFees(request.getCleaningFees());
        builder.fuelCharges(request.getFuelCharges());

        Contract contract = builder.build();
        Contract saved = contractRepository.save(contract);

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
        updateField(request.getDepositAmount(), contract::setDepositAmount);
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
    public void deleteContract(Long id) {
        Contract contract = fetchContractInTenant(id);
        contractRepository.delete(contract);
        log.info("Deleted contract [id={}] from tenant [{}]", id, TenantContext.getCurrentTenantId());
    }

    // ── SIGNATURE WORKFLOW ───────────────────────────────────────────────────

    @Transactional
    public ContractResponse signContract(Long id, ContractSignatureRequest request) {
        Contract contract = fetchContractInTenant(id);
        Long tenantId = TenantContext.getCurrentTenantId();

        // ── Enforce signature order: Agency MUST sign first ────────────────────
        if (request.getSignerType() == ContractSignatureRequest.SignerType.CLIENT) {
            if (contract.getOwnerSignature() == null || contract.getOwnerSignature().isEmpty()) {
                throw new IllegalStateException("Agency must sign the contract before the client can sign.");
            }
        }

        if (request.getSignerType() == ContractSignatureRequest.SignerType.OWNER) {
            // Auto-apply agency signature from tenant settings
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            String agencySig = tenant.getAgencySignature();
            if (agencySig != null && !agencySig.isEmpty()) {
                contract.setOwnerSignature(agencySig);
            } else {
                contract.setOwnerSignature(request.getSignatureData());
            }
            contract.setOwnerSignedAt(LocalDateTime.now());
            contract.setOwnerSignedIp(request.getIpAddress());
            contract.setOwnerUserAgent(request.getUserAgent());

            // Create notification
            notificationService.createNotification(
                    "Contract Signed by Agency",
                    "Contract " + contract.getContractNumber() + " has been signed by the agency.",
                    Notification.NotificationType.CONTRACT_SIGNED_AGENCY,
                    contract.getId(), tenantId);

        } else if (request.getSignerType() == ContractSignatureRequest.SignerType.CLIENT) {
            contract.setClientSignature(request.getSignatureData());
            contract.setClientSignedAt(LocalDateTime.now());
            contract.setClientSignedIp(request.getIpAddress());
            contract.setClientUserAgent(request.getUserAgent());
        } else if (request.getSignerType() == ContractSignatureRequest.SignerType.EMPLOYEE) {
            contract.setEmployeeSignature(request.getSignatureData());
        }

        if (request.getTermsAccepted() != null && request.getTermsAccepted()) {
            contract.setTermsAccepted(true);
            contract.setTermsAcceptedAt(LocalDateTime.now());
        }

        contract.setSignedAt(LocalDateTime.now());
        updateStatusAfterSigning(contract);

        Contract saved = contractRepository.save(contract);
        logAudit(saved, "SIGN", request.getSignerType() + " signed contract", null, null);

        // ── Regenerate and save PDF after any signature ────────────────────────
        try {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            com.carrental.entity.Deposit deposit = depositRepository.findByContractId(saved.getId()).orElse(null);
            byte[] pdf = pdfService.generateContractPdf(saved, tenant, deposit);
            String pdfUrl = pdfService.saveContractPdf(saved, pdf);
            saved.setPdfUrl(pdfUrl);
            saved = contractRepository.save(saved);
            log.info("PDF regenerated and saved for contract [id={}] after {} signature", saved.getId(), request.getSignerType());
        } catch (Exception e) {
            log.error("Failed to regenerate PDF after signing contract [id={}]", saved.getId(), e);
        }

        log.info("Contract [id={}] signed by {} in tenant [{}]", id, request.getSignerType(), tenantId);
        return ContractResponse.from(saved);
    }

    @Transactional
    public String generateQrToken(Long id, String overrideFrontendUrl) {
        Contract contract = fetchContractInTenant(id);
        Long tenantId = TenantContext.getCurrentTenantId();

        // ── Enforce: agency must sign before QR generation ─────────────────────
        if (contract.getOwnerSignature() == null || contract.getOwnerSignature().isEmpty()) {
            throw new IllegalStateException("Agency must sign the contract before generating a QR code.");
        }

        String token = generateSecureToken();
        contract.setQrToken(token);
        // Use frontend-provided URL (for network access) or fallback to configured URL
        String url = (overrideFrontendUrl != null && !overrideFrontendUrl.isBlank())
                ? overrideFrontendUrl
                : frontendUrl;
        contract.setPublicSigningUrl(url + "/contract-sign/" + token);
        contract.setStatus(ContractStatus.PENDING_SIGNATURE);
        Contract saved = contractRepository.save(contract);

        logAudit(saved, "QR_GENERATED", "QR token generated for contract", null, null);
        log.info("Generated QR token for contract [id={}]", id);

        notificationService.createNotification(
                "QR Code Generated",
                "QR code for contract " + saved.getContractNumber() + " is ready to share with the client.",
                Notification.NotificationType.QR_GENERATED,
                saved.getId(), tenantId);

        // Send email to client if they have one
        emailService.sendContractReadyEmail(
                saved.getClientEmail(),
                saved.getClientFullName(),
                saved.getContractNumber(),
                saved.getPublicSigningUrl(),
                tenantRepository.findById(tenantId).map(Tenant::getName).orElse("Your Agency"));

        return token;
    }

    @Transactional
    public ContractResponse finalizeContract(Long id) {
        Contract contract = fetchContractInTenant(id);
        if (!hasRequiredSignatures(contract)) {
            throw new IllegalStateException("Cannot finalize: required signatures missing");
        }
        contract.setStatus(ContractStatus.ACTIVE);
        if (contract.getReservation() != null) {
            contract.getReservation().setStatus(ReservationStatus.ACTIVE);
            reservationRepository.save(contract.getReservation());
        }
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
        if (contract.getReservation() != null) {
            contract.getReservation().setStatus(ReservationStatus.COMPLETED);
            reservationRepository.save(contract.getReservation());
        }
        if (contract.getVehicle() != null) {
            boolean reserved = contract.getReservation() != null
                    && reservationRepository.existsByVehicleIdAndTenantIdAndIdNotAndStatusIn(
                    contract.getVehicle().getId(),
                    contract.getTenant().getId(),
                    contract.getReservation().getId(),
                    List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.ACTIVE));
            contract.getVehicle().setStatut(reserved ? VehicleStatus.RESERVED : VehicleStatus.AVAILABLE);
            vehicleRepository.save(contract.getVehicle());
        }
        Contract saved = contractRepository.save(contract);
        logAudit(saved, "COMPLETE", "Contract marked as completed", null, null);
        return ContractResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public byte[] generateContractPdf(Long id) {
        Contract contract = fetchContractInTenant(id);
        Tenant tenant = tenantRepository.findById(contract.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        com.carrental.entity.Deposit deposit = depositRepository.findByContractId(contract.getId()).orElse(null);
        return pdfService.generateContractPdf(contract, tenant, deposit);
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
                .pdfUrl(contract.getPdfUrl())
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
        notificationService.createNotification(
                "Contract Fully Signed",
                "Client signed contract " + saved.getContractNumber() + ". The contract is now fully signed.",
                Notification.NotificationType.CONTRACT_FULLY_SIGNED,
                saved.getId(), tenantId);

        // ── Send confirmation email to client ──────────────────────────────────
        emailService.sendContractSignedEmail(
                saved.getClientEmail(),
                saved.getClientFullName(),
                saved.getContractNumber(),
                saved.getTenant().getName());

        // ── Auto-regenerate and save PDF ───────────────────────────────────────
        try {
            com.carrental.entity.Deposit deposit = depositRepository.findByContractId(saved.getId()).orElse(null);
            byte[] pdf = pdfService.generateContractPdf(saved, saved.getTenant(), deposit);
            String pdfUrl = pdfService.saveContractPdf(saved, pdf);
            saved.setPdfUrl(pdfUrl);
            saved = contractRepository.save(saved);
            log.info("PDF regenerated and saved for fully signed contract [id={}]", saved.getId());
            logAudit(saved, "PDF_GENERATED", "PDF auto-regenerated and saved after client signature", null, null);
        } catch (Exception e) {
            log.error("Failed to regenerate PDF for contract [id={}]", saved.getId(), e);
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

    @Transactional
    public ContractResponse createFromReservation(Long reservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Reservation reservation = reservationRepository.findByIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + reservationId));
        if (reservation.getContract() != null) {
            return ContractResponse.from(reservation.getContract());
        }

        Client client = reservation.getClient();
        Vehicle vehicle = reservation.getVehicle();

        if (client == null) {
            throw new IllegalArgumentException("Reservation has no linked client. Please assign a client first.");
        }
        if (vehicle == null) {
            throw new IllegalArgumentException("Reservation has no linked vehicle.");
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(reservation.getDateStart(), reservation.getDateEnd()) + 1;
        java.math.BigDecimal totalPrice = vehicle.getPrixJour().multiply(java.math.BigDecimal.valueOf(days));
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
                .pickupTime(reservation.getStartTime())
                .returnTime(reservation.getEndTime())
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
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        // Link existing deposit from reservation to the new contract
        try {
            depositService.linkDepositToContract(reservationId, saved);
        } catch (Exception e) {
            log.warn("Failed to link deposit to contract [id={}] from reservation [id={}]", saved.getId(), reservationId, e);
        }

        logAudit(saved, "CREATE", "Contract auto-generated from reservation " + reservationId, null, null);
        log.info("Created contract [id={}] from reservation [id={}] in tenant [{}]", saved.getId(), reservationId, tenantId);
        return ContractResponse.from(saved);
    }

    // ── AUTO GENERATION ──────────────────────────────────────────────────────

    @Transactional
    public String generateContractNumber() {
        Long tenantId = TenantContext.getCurrentTenantId();
        String prefix = "CTR";
        String year = String.valueOf(java.time.Year.now().getValue());
        long count = contractRepository.count() + 1;
        String number = String.format("%s-%s-%05d", prefix, year, count);

        // Ensure uniqueness
        while (contractRepository.findByContractNumber(number).isPresent()) {
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

    private <T> void updateField(T value, java.util.function.Consumer<T> setter) {
        if (value != null) setter.accept(value);
    }

    private boolean hasRequiredSignatures(Contract contract) {
        boolean client = contract.getClientSignature() != null && !contract.getClientSignature().isEmpty();
        boolean owner = contract.getOwnerSignature() != null && !contract.getOwnerSignature().isEmpty();
        return client && owner;
    }

    private void updateStatusAfterSigning(Contract contract) {
        boolean client = contract.getClientSignature() != null && !contract.getClientSignature().isEmpty();
        boolean owner = contract.getOwnerSignature() != null && !contract.getOwnerSignature().isEmpty();

        if (client && owner) {
            contract.setStatus(ContractStatus.SIGNED);
        } else if (client || owner) {
            contract.setStatus(ContractStatus.PARTIALLY_SIGNED);
        }
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
