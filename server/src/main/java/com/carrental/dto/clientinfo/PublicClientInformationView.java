package com.carrental.dto.clientinfo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public (unauthenticated) view of a request — deliberately contains no
 * internal ID, tenant ID, contract ID, or admin-only data (spec section 4).
 * The token itself (in the URL) is the only identifier the client ever sees.
 *
 * <p>Progressive disclosure: when this request is linked to an existing
 * {@link com.carrental.entity.Client} (see ClientInformationRequest#clientId),
 * {@code known*} carries that client's own values — shown back to them as
 * confirmed/read-only with an edit option — and {@link #missingFields} lists
 * exactly which fields still need to be collected, so the public form never
 * re-asks for information the agency already has on file.
 */
@Data
@Builder
public class PublicClientInformationView {
    private String temporaryName;
    private String preferredLanguage;
    private String agencyName;
    private String agencyLogo;
    private LocalDateTime expiresAt;
    /** True once the client has already submitted — form should show a read-only confirmation instead. */
    private boolean alreadySubmitted;

    /** True when this request is linked to an existing client — the client below is only meaningful then. */
    private boolean hasKnownClient;

    private String knownFullName;
    private String knownPhone;
    private String knownSecondaryPhone;
    private String knownEmail;
    private String knownGender;
    private LocalDate knownBirthDate;
    private String knownNationality;
    private String knownAddress;
    private String knownCity;
    private String knownCountry;
    /** "CIN" or "PASSPORT" — whichever the client record already has a number for. */
    private String knownDocumentType;
    private String knownDocumentNumber;
    private String knownDriverLicenseNumber;
    private String knownCompanyName;

    /**
     * Field keys (matching {@link ClientInformationSubmitRequest}'s property names —
     * fullName, phone, secondaryPhone, email, gender, birthDate, nationality, address,
     * city, country, documentNumber, driverLicenseNumber, companyName) that have no
     * known value yet. The public form should render only these as blank inputs; every
     * other field is shown as an already-provided, editable-on-request value.
     */
    private List<String> missingFields;
}
