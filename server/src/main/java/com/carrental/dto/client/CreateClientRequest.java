package com.carrental.dto.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/clients} — create a client.
 */
@Data
public class CreateClientRequest {

    @JsonAlias("name")
    @NotBlank(message = "Full name is required")
    private String fullName;

    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String secondaryPhone;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Country is required")
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
