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
    // Deliberately no issue/expiry dates: the client-facing form only ever
    // collects the document number itself — dates (if an agency needs them
    // at all) are captured later through the admin's own Client record.
    @NotNull
    private DocumentType documentType;
    @NotBlank
    private String documentNumber;

    // ── Address ──────────────────────────────────────────────────────────
    // No postal code: dropped from the public form entirely (spec section 2)
    // — Client.postalCode/Contract.clientPostalCode stay nullable and unused
    // for submissions coming through this flow.
    private String address;
    private String city;
    private String country;
    // ISO 3166-1 alpha-2 code from the country selector. Stored only in the
    // request's raw submission_payload JSON for now — Client/Contract have
    // no dedicated country-code column, so approval still maps `country`
    // (the name) onto the existing free-text column.
    private String countryCode;

    // ── Driver license ───────────────────────────────────────────────────
    // Single field, no dates — same reasoning as the identity document above.
    private String driverLicenseNumber;

    // ── Optional ─────────────────────────────────────────────────────────
    private String companyName;
    private String notes;

    // ── Consent (spec section 25 — never pre-checked, enforced server-side) ──
    @NotNull
    private Boolean privacyAccepted;
}
