package com.carrental.dto.contract;

import com.carrental.entity.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete read-only contract projection.
 */
@Data
@Builder
public class ContractResponse {

    private Long id;
    private String contractNumber;
    private ContractStatus status;
    private String contractType;
    private String contractLanguage;
    private Integer contractDuration;

    // Links
    private Long reservationId;
    private Long clientId;
    private Long vehicleId;
    /** True when {@code vehicleId} points at a vehicle that no longer exists (soft-deleted). Set by the service layer. */
    private boolean vehicleMissing;

    // Dates
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate pickupDate;
    private LocalDate returnDate;
    private LocalTime pickupTime;
    private LocalTime returnTime;
    private String pickupLocation;
    private String returnLocation;

    // Client
    private String clientFirstName;
    private String clientLastName;
    private String clientFullName;
    private String clientNationality;
    private String clientGender;
    private LocalDate clientBirthDate;
    private String clientCin;
    private String clientPassportNumber;
    private String clientDriverLicense;
    private LocalDate clientDriverLicenseIssue;
    private LocalDate clientDriverLicenseExpiry;
    private String clientAddress;
    private String clientCity;
    private String clientCountry;
    private String clientPostalCode;
    private String clientPhone;
    private String clientSecondaryPhone;
    private String clientEmail;
    private String emergencyContactName;
    private String emergencyContactPhone;

    // Vehicle
    private String vehicleBrand;
    private String vehicleModel;
    private String vehicleCategory;
    private Integer vehicleYear;
    private String vehicleColor;
    private String vehicleRegistration;
    private String vehicleVin;
    private String vehicleTransmission;
    private String insuranceProvider;
    private LocalDate insuranceExpiration;
    private LocalDate technicalInspectionExpiration;

    // Rental
    private String pickupAgency;
    private String returnAgency;
    private String pickupAgent;
    private String returnAgent;
    private Integer rentalDays;
    private Integer extraHours;
    private BigDecimal deliveryFees;
    private BigDecimal returnFees;
    private BigDecimal lateFees;
    private BigDecimal cleaningFees;
    private BigDecimal fuelCharges;

    // Payment
    private BigDecimal totalPrice;
    private BigDecimal dailyPrice;
    private BigDecimal depositAmount;
    private String depositCurrency;
    private com.carrental.entity.DepositStatus depositStatus;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private String paymentMethod;
    private String paymentStatus;
    private LocalDate paymentDate;
    private String invoiceNumber;

    // Fuel
    private String fuelType;
    private String fuelLevelStart;
    private String fuelLevelEnd;

    // Signatures
    private Boolean clientSigned;
    private Boolean ownerSigned;
    private Boolean employeeSigned;
    private Boolean termsAccepted;
    private LocalDateTime termsAcceptedAt;
    private LocalDateTime signedAt;

    // Signature images (base64 data URLs)
    private String clientSignature;
    private String ownerSignature;
    private String employeeSignature;
    private LocalDateTime clientSignedAt;
    private LocalDateTime ownerSignedAt;

    // QR & PDF
    private String qrToken;
    private String publicSigningUrl;
    private String pdfUrl;

    // Deposit
    private com.carrental.dto.deposit.DepositResponse deposit;

    // Metadata
    private String notes;
    private String generatedBy;
    private String lastModifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long tenantId;

    // Related entities
    private List<AdditionalDriverDto> additionalDrivers;
    private VehicleConditionDto vehicleCondition;
    private List<ContractDocumentDto> documents;
    private List<ContractAuditLogDto> auditLogs;
    private List<com.carrental.dto.payment.PaymentResponse> payments;

    // ── Static factory ───────────────────────────────────────────────────────

    public static ContractResponse from(Contract contract) {
        return ContractResponse.builder()
                .id(contract.getId())
                .contractNumber(contract.getContractNumber())
                .status(contract.getStatus())
                .contractType(contract.getContractType())
                .contractLanguage(contract.getContractLanguage())
                .contractDuration(contract.getContractDuration())
                .reservationId(contract.getReservation() != null ? contract.getReservation().getId() : null)
                .clientId(contract.getClient() != null ? contract.getClient().getId() : null)
                .vehicleId(contract.getVehicle() != null ? contract.getVehicle().getId() : null)
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .pickupDate(contract.getPickupDate())
                .returnDate(contract.getReturnDate())
                .pickupTime(contract.getPickupTime())
                .returnTime(contract.getReturnTime())
                .pickupLocation(contract.getPickupLocation())
                .returnLocation(contract.getReturnLocation())
                .clientFirstName(contract.getClientFirstName())
                .clientLastName(contract.getClientLastName())
                .clientFullName(contract.getClientFullName())
                .clientNationality(contract.getClientNationality())
                .clientGender(contract.getClientGender())
                .clientBirthDate(contract.getClientBirthDate())
                .clientCin(contract.getClientCin())
                .clientPassportNumber(contract.getClientPassportNumber())
                .clientDriverLicense(contract.getClientDriverLicense())
                .clientDriverLicenseIssue(contract.getClientDriverLicenseIssue())
                .clientDriverLicenseExpiry(contract.getClientDriverLicenseExpiry())
                .clientAddress(contract.getClientAddress())
                .clientCity(contract.getClientCity())
                .clientCountry(contract.getClientCountry())
                .clientPostalCode(contract.getClientPostalCode())
                .clientPhone(contract.getClientPhone())
                .clientSecondaryPhone(contract.getClientSecondaryPhone())
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
                .insuranceProvider(contract.getInsuranceProvider())
                .insuranceExpiration(contract.getInsuranceExpiration())
                .technicalInspectionExpiration(contract.getTechnicalInspectionExpiration())
                .pickupAgency(contract.getPickupAgency())
                .returnAgency(contract.getReturnAgency())
                .pickupAgent(contract.getPickupAgent())
                .returnAgent(contract.getReturnAgent())
                .rentalDays(contract.getRentalDays())
                .extraHours(contract.getExtraHours())
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
                .paymentStatus(contract.getPaymentStatus())
                .paymentDate(contract.getPaymentDate())
                .invoiceNumber(contract.getInvoiceNumber())
                .fuelType(contract.getFuelType())
                .fuelLevelStart(contract.getFuelLevelStart())
                .fuelLevelEnd(contract.getFuelLevelEnd())
                .clientSigned(contract.getClientSignature() != null && !contract.getClientSignature().isEmpty())
                .ownerSigned(contract.getOwnerSignature() != null && !contract.getOwnerSignature().isEmpty())
                .employeeSigned(contract.getEmployeeSignature() != null && !contract.getEmployeeSignature().isEmpty())
                .clientSignature(contract.getClientSignature())
                .ownerSignature(contract.getOwnerSignature())
                .employeeSignature(contract.getEmployeeSignature())
                .clientSignedAt(contract.getClientSignedAt())
                .ownerSignedAt(contract.getOwnerSignedAt())
                .termsAccepted(contract.getTermsAccepted())
                .termsAcceptedAt(contract.getTermsAcceptedAt())
                .signedAt(contract.getSignedAt())
                .qrToken(contract.getQrToken())
                .publicSigningUrl(contract.getPublicSigningUrl())
                .pdfUrl(cacheBustedPdfUrl(contract.getPdfUrl(), contract.getUpdatedAt()))
                .deposit(null) // set externally by service layer
                .notes(contract.getNotes())
                .generatedBy(contract.getGeneratedBy())
                .lastModifiedBy(contract.getLastModifiedBy())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .tenantId(contract.getTenant() != null ? contract.getTenant().getId() : null)
                .additionalDrivers(contract.getAdditionalDrivers() != null
                        ? contract.getAdditionalDrivers().stream()
                            .map(d -> AdditionalDriverDto.builder()
                                .id(d.getId())
                                .fullName(d.getFullName())
                                .cin(d.getCin())
                                .passportNumber(d.getPassportNumber())
                                .driverLicenseNumber(d.getDriverLicenseNumber())
                                .nationality(d.getNationality())
                                .address(d.getAddress())
                                .phone(d.getPhone())
                                .birthDate(d.getBirthDate())
                                .build())
                            .collect(Collectors.toList())
                        : null)
                .vehicleCondition(contract.getVehicleCondition() != null
                        ? VehicleConditionDto.builder()
                            .id(contract.getVehicleCondition().getId())
                            .frontDamage(contract.getVehicleCondition().getFrontDamage())
                            .rearDamage(contract.getVehicleCondition().getRearDamage())
                            .leftSideDamage(contract.getVehicleCondition().getLeftSideDamage())
                            .rightSideDamage(contract.getVehicleCondition().getRightSideDamage())
                            .windshieldDamage(contract.getVehicleCondition().getWindshieldDamage())
                            .interiorDamage(contract.getVehicleCondition().getInteriorDamage())
                            .roofDamage(contract.getVehicleCondition().getRoofDamage())
                            .bumperFrontDamage(contract.getVehicleCondition().getBumperFrontDamage())
                            .bumperRearDamage(contract.getVehicleCondition().getBumperRearDamage())
                            .hoodDamage(contract.getVehicleCondition().getHoodDamage())
                            .trunkDamage(contract.getVehicleCondition().getTrunkDamage())
                            .tireCondition(contract.getVehicleCondition().getTireCondition())
                            .scratchDescription(contract.getVehicleCondition().getScratchDescription())
                            .dentDescription(contract.getVehicleCondition().getDentDescription())
                            .generalNotes(contract.getVehicleCondition().getGeneralNotes())
                            .conditionPhotos(contract.getVehicleCondition().getConditionPhotos())
                            .inspectionDate(contract.getVehicleCondition().getInspectionDate())
                            .inspectedBy(contract.getVehicleCondition().getInspectedBy())
                            .isPickupInspection(contract.getVehicleCondition().getIsPickupInspection())
                            .build()
                        : null)
                .documents(contract.getDocuments() != null
                        ? contract.getDocuments().stream()
                            .map(d -> ContractDocumentDto.builder()
                                .id(d.getId())
                                .documentType(d.getDocumentType())
                                .documentName(d.getDocumentName())
                                .documentUrl(d.getDocumentUrl())
                                .isPresent(d.getIsPresent())
                                .verifiedAt(d.getVerifiedAt())
                                .verifiedBy(d.getVerifiedBy())
                                .build())
                            .collect(Collectors.toList())
                        : null)
                .auditLogs(contract.getAuditLogs() != null
                        ? contract.getAuditLogs().stream()
                            .map(l -> ContractAuditLogDto.builder()
                                .id(l.getId())
                                .action(l.getAction())
                                .description(l.getDescription())
                                .performedBy(l.getPerformedBy())
                                .oldValue(l.getOldValue())
                                .newValue(l.getNewValue())
                                .createdAt(l.getCreatedAt())
                                .build())
                            .collect(Collectors.toList())
                        : null)
                .build();
    }

    /**
     * Appends a version query param derived from the contract's last-updated
     * timestamp so the browser never reuses a cached PDF response after the
     * agency's branding/settings change and the PDF is regenerated.
     */
    private static String cacheBustedPdfUrl(String pdfUrl, LocalDateTime updatedAt) {
        if (pdfUrl == null || pdfUrl.isBlank()) return pdfUrl;
        long version = updatedAt != null
                ? updatedAt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                : System.currentTimeMillis();
        String separator = pdfUrl.contains("?") ? "&" : "?";
        return pdfUrl + separator + "v=" + version;
    }
}

