package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete professional rental contract entity.
 * Supports digital contracts, QR signing, PDF generation, and full rental workflow.
 */
@Entity
@Table(
    name = "contracts",
    indexes = {
        @Index(name = "idx_contract_tenant", columnList = "tenant_id"),
        @Index(name = "idx_contract_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_contract_qr_token", columnList = "qr_token", unique = true),
        @Index(name = "idx_contract_number", columnList = "contract_number", unique = true),
        @Index(name = "idx_contract_reservation", columnList = "reservation_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core Contract Info ───────────────────────────────────────────────────

    @Column(name = "contract_number", nullable = false, unique = true, length = 50)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_status", nullable = false, length = 30)
    private ContractStatus status;

    @Column(name = "contract_type", length = 30)
    private String contractType;

    @Column(name = "contract_language", length = 10)
    private String contractLanguage;

    @Column(name = "contract_duration")
    private Integer contractDuration;

    // ── Linked Entities ──────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    // ── Dates & Times ────────────────────────────────────────────────────────

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "pickup_date")
    private LocalDate pickupDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "pickup_time")
    private LocalTime pickupTime;

    @Column(name = "return_time")
    private LocalTime returnTime;

    @Column(name = "pickup_location", length = 255)
    private String pickupLocation;

    @Column(name = "return_location", length = 255)
    private String returnLocation;

    // ── Client Information ───────────────────────────────────────────────────

    @Column(name = "client_first_name", length = 100)
    private String clientFirstName;

    @Column(name = "client_last_name", length = 100)
    private String clientLastName;

    @Column(name = "client_full_name", length = 200)
    private String clientFullName;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "client_nationality", length = 50)
    private String clientNationality;

    @Column(name = "client_gender", length = 10)
    private String clientGender;

    @Column(name = "client_birth_date")
    private LocalDate clientBirthDate;

    @Column(name = "client_cin", length = 50)
    private String clientCin;

    @Column(name = "client_passport_number", length = 50)
    private String clientPassportNumber;

    @Column(name = "client_driver_license", length = 50)
    private String clientDriverLicense;

    @Column(name = "client_driver_license_issue")
    private LocalDate clientDriverLicenseIssue;

    @Column(name = "client_driver_license_expiry")
    private LocalDate clientDriverLicenseExpiry;

    @Column(name = "client_address", length = 255)
    private String clientAddress;

    @Column(name = "client_city", length = 100)
    private String clientCity;

    @Column(name = "client_country", length = 100)
    private String clientCountry;

    @Column(name = "client_postal_code", length = 20)
    private String clientPostalCode;

    @Column(name = "client_phone", length = 50)
    private String clientPhone;

    @Column(name = "client_secondary_phone", length = 50)
    private String clientSecondaryPhone;

    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    // ── Vehicle Information ──────────────────────────────────────────────────

    @Column(name = "vehicle_brand", length = 100)
    private String vehicleBrand;

    @Column(name = "vehicle_model", length = 100)
    private String vehicleModel;

    @Column(name = "vehicle_category", length = 50)
    private String vehicleCategory;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    @Column(name = "vehicle_color", length = 30)
    private String vehicleColor;

    @Column(name = "vehicle_registration", length = 50)
    private String vehicleRegistration;

    @Column(name = "vehicle_vin", length = 50)
    private String vehicleVin;

    @Column(name = "vehicle_transmission", length = 20)
    private String vehicleTransmission;

    @Column(name = "insurance_provider", length = 100)
    private String insuranceProvider;

    @Column(name = "insurance_expiration")
    private LocalDate insuranceExpiration;

    @Column(name = "technical_inspection_expiration")
    private LocalDate technicalInspectionExpiration;

    // ── Rental Details ───────────────────────────────────────────────────────

    @Column(name = "pickup_agency", length = 150)
    private String pickupAgency;

    @Column(name = "return_agency", length = 150)
    private String returnAgency;

    @Column(name = "pickup_agent", length = 150)
    private String pickupAgent;

    @Column(name = "return_agent", length = 150)
    private String returnAgent;

    @Column(name = "rental_days")
    private Integer rentalDays;

    @Column(name = "extra_hours")
    private Integer extraHours;

    @Column(name = "allowed_mileage")
    private Integer allowedMileage;

    @Column(name = "extra_mileage_cost", precision = 10, scale = 2)
    private BigDecimal extraMileageCost;

    @Column(name = "delivery_fees", precision = 10, scale = 2)
    private BigDecimal deliveryFees;

    @Column(name = "return_fees", precision = 10, scale = 2)
    private BigDecimal returnFees;

    @Column(name = "late_fees", precision = 10, scale = 2)
    private BigDecimal lateFees;

    @Column(name = "cleaning_fees", precision = 10, scale = 2)
    private BigDecimal cleaningFees;

    @Column(name = "fuel_charges", precision = 10, scale = 2)
    private BigDecimal fuelCharges;

    // ── Payment Information ──────────────────────────────────────────────────

    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "daily_price", precision = 10, scale = 2)
    private BigDecimal dailyPrice;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "remaining_amount", precision = 10, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    // ── Fuel & Mileage ───────────────────────────────────────────────────────

    @Column(name = "fuel_type", length = 20)
    private String fuelType;

    @Column(name = "fuel_level_start", length = 20)
    private String fuelLevelStart;

    @Column(name = "fuel_level_end", length = 20)
    private String fuelLevelEnd;

    @Column(name = "mileage_start")
    private Integer mileageStart;

    @Column(name = "mileage_end")
    private Integer mileageEnd;

    // ── Digital Signatures ───────────────────────────────────────────────────

    @Column(name = "client_signature", columnDefinition = "TEXT")
    private String clientSignature;

    @Column(name = "owner_signature", columnDefinition = "TEXT")
    private String ownerSignature;

    @Column(name = "employee_signature", columnDefinition = "TEXT")
    private String employeeSignature;

    @Column(name = "terms_accepted")
    private Boolean termsAccepted = false;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    // ── Per-Signer Metadata ──────────────────────────────────────────────────

    @Column(name = "client_signed_at")
    private LocalDateTime clientSignedAt;

    @Column(name = "owner_signed_at")
    private LocalDateTime ownerSignedAt;

    @Column(name = "client_signed_ip", length = 50)
    private String clientSignedIp;

    @Column(name = "owner_signed_ip", length = 50)
    private String ownerSignedIp;

    @Column(name = "client_user_agent", length = 255)
    private String clientUserAgent;

    @Column(name = "owner_user_agent", length = 255)
    private String ownerUserAgent;

    // ── QR & Public Signing ──────────────────────────────────────────────────

    @Column(name = "qr_token", unique = true, length = 128)
    private String qrToken;

    @Column(name = "public_signing_url", length = 512)
    private String publicSigningUrl;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    // ── Notes & Metadata ─────────────────────────────────────────────────────

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "generated_by", length = 150)
    private String generatedBy;

    @Column(name = "last_modified_by", length = 150)
    private String lastModifiedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Related Entities ─────────────────────────────────────────────────────

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AdditionalDriver> additionalDrivers = new ArrayList<>();

    @OneToOne(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private VehicleCondition vehicleCondition;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContractDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContractAuditLog> auditLogs = new ArrayList<>();

    // ── Multi-tenancy ────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ── Lifecycle callbacks ──────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ContractStatus.DRAFT;
        if (termsAccepted == null) termsAccepted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
