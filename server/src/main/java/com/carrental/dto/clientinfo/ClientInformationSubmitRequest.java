package com.carrental.dto.clientinfo;

import com.carrental.entity.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * What the client submits on the public self-fill form. Deliberately
 * contains ONLY client-owned fields — see spec section 17 ("contract data
 * ownership"): no price, deposit, vehicle, dates, status, or agency fields
 * exist here, so there is no field a client could submit to affect those.
 */
@Data
public class ClientInformationSubmitRequest {

    // ── Personal information ────────────────────────────────────────────
    @NotBlank
    private String fullName;
    @NotBlank
    private String phone;
    private String secondaryPhone;
    @Email
    private String email;
    private String gender;
    private LocalDate birthDate;
    private String nationality;

    // ── Identity document (one selector, one number — spec section 6) ──
    @NotNull
    private DocumentType documentType;
    @NotBlank
    private String documentNumber;
    private LocalDate documentIssueDate;
    private LocalDate documentExpiryDate;
    private String documentIssuingCountry;

    // ── Address ──────────────────────────────────────────────────────────
    private String address;
    private String city;
    private String postalCode;
    private String country;

    // ── Driver license ───────────────────────────────────────────────────
    private String driverLicenseNumber;
    private String driverLicenseCategory;
    private LocalDate driverLicenseIssueDate;
    private LocalDate driverLicenseExpiryDate;
    private String driverLicenseCountry;

    // ── Optional ─────────────────────────────────────────────────────────
    private String companyName;
    private String notes;

    // ── Consent (spec section 25 — never pre-checked, enforced server-side) ──
    @NotNull
    private Boolean privacyAccepted;
}
