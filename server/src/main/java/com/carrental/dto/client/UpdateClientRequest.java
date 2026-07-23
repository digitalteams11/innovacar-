package com.carrental.dto.client;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/clients/{id}} — partial update.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateClientRequest {

    private String name;

    @Email(message = "Must be a valid email address")
    private String email;

    private String phone;

    private String secondaryPhone;

    private String address;

    private String city;

    private String country;

    private String postalCode;

    private String nationality;

    private String gender;

    private LocalDate birthDate;

    /** @deprecated use documentType=CIN + documentNumber. Kept for the legacy desktop app. */
    @Deprecated
    private String cin;

    /** @deprecated use documentType=PASSPORT + documentNumber. Kept for the legacy desktop app. */
    @Deprecated
    private String passportNumber;

    // ── Unified identity document (replaces the cin/passportNumber pair above) ──

    private com.carrental.entity.DocumentType documentType;

    private String documentNumber;

    private String documentIssuingCountry;

    private String documentIssuingAuthority;

    private LocalDate documentIssueDate;

    private LocalDate documentExpiryDate;

    private String documentNotes;

    private String drivingLicense;

    private LocalDate drivingLicenseIssue;

    private LocalDate drivingLicenseExpiry;

    private String drivingLicenseCategory;

    private String drivingLicenseCountry;

    private String emergencyContactName;

    private String emergencyContactPhone;

    private String companyName;

    private String notes;
}
