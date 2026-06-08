package com.carrental.dto.contract;

import com.carrental.entity.ContractStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public read-only contract projection for the signing page.
 */
@Data
@Builder
public class PublicContractResponse {

    private String contractNumber;
    private ContractStatus status;
    private String contractType;
    private String contractLanguage;
    private Integer contractDuration;

    // Dates
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate pickupDate;
    private LocalDate returnDate;
    private String pickupLocation;
    private String returnLocation;

    // Client
    private String clientFirstName;
    private String clientLastName;
    private String clientFullName;
    private String clientNationality;
    private String clientCin;
    private String clientPassportNumber;
    private String clientDriverLicense;
    private String clientAddress;
    private String clientCity;
    private String clientCountry;
    private String clientPhone;
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

    // Rental
    private String pickupAgency;
    private String returnAgency;
    private Integer rentalDays;
    private Integer allowedMileage;
    private BigDecimal extraMileageCost;
    private BigDecimal deliveryFees;
    private BigDecimal returnFees;
    private BigDecimal lateFees;
    private BigDecimal cleaningFees;
    private BigDecimal fuelCharges;

    // Payment
    private BigDecimal totalPrice;
    private BigDecimal dailyPrice;
    private BigDecimal depositAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private String paymentMethod;

    // Fuel & Mileage
    private String fuelType;
    private String fuelLevelStart;
    private String fuelLevelEnd;
    private Integer mileageStart;
    private Integer mileageEnd;

    // Signing status
    private Boolean clientSigned;
    private Boolean ownerSigned;
    private Boolean employeeSigned;
    private Boolean termsAccepted;
    private LocalDateTime signedAt;

    // Signature data (visible to client)
    private String ownerSignature;
    private String clientSignature;
    private LocalDateTime ownerSignedAt;
    private LocalDateTime clientSignedAt;
    private String agencyStampUrl;
    private String pdfUrl;

    // Agency
    private String agencyName;
    private String agencyAddress;
    private String agencyCity;
    private String agencyCountry;
    private String agencyPhone;
    private String agencyEmail;
    private String agencyLogo;

    // Condition
    private VehicleConditionDto vehicleCondition;

    // Terms
    private List<String> terms;

    // Deposit
    private com.carrental.dto.deposit.DepositResponse deposit;

    // Related
    private List<AdditionalDriverDto> additionalDrivers;
}
