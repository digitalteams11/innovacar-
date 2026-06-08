package com.carrental.dto.contract;

import com.carrental.entity.ContractStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Complete request body for updating a rental contract.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateContractRequest {

    private String contractNumber;
    private ContractStatus status;
    private String contractType;
    private String contractLanguage;
    private Integer contractDuration;

    private Long reservationId;
    private Long clientId;
    private Long vehicleId;

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
    private String paymentStatus;
    private LocalDate paymentDate;
    private String invoiceNumber;

    // Fuel & Mileage
    private String fuelType;
    private String fuelLevelStart;
    private String fuelLevelEnd;
    private Integer mileageStart;
    private Integer mileageEnd;

    // Related
    private List<AdditionalDriverDto> additionalDrivers;
    private VehicleConditionDto vehicleCondition;
    private List<ContractDocumentDto> documents;

    private String notes;
}
