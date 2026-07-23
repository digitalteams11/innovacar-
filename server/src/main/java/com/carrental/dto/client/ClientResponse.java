package com.carrental.dto.client;

import com.carrental.entity.Client;
import com.carrental.entity.ClientIdentityDocument;
import com.carrental.entity.DocumentType;
import com.carrental.service.ClientService;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only client projection returned by all client endpoints.
 */
@Data
@Builder
public class ClientResponse {

    private Long   id;
    private String fullName;
    private String name;
    private String firstName;
    private String lastName;
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

    /** @deprecated use documentType/documentNumber. Kept for the legacy desktop app. */
    @Deprecated
    private String cin;
    /** @deprecated use documentType/documentNumber. Kept for the legacy desktop app. */
    @Deprecated
    private String passportNumber;

    // ── Unified identity document ───────────────────────────────────────────
    private DocumentType documentType;
    /** Full document number — only populated when the caller has VIEW_CLIENT_DOCUMENTS_FULL. */
    private String documentNumber;
    /** Always-safe masked form, e.g. "AB••••56" — shown regardless of permission. */
    private String documentMasked;
    private String documentIssuingCountry;
    private String documentIssuingAuthority;
    private LocalDate documentIssueDate;
    private LocalDate documentExpiryDate;

    private String drivingLicense;
    private LocalDate drivingLicenseIssue;
    private LocalDate drivingLicenseExpiry;
    private String drivingLicenseCategory;
    private String drivingLicenseCountry;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String companyName;
    private String notes;
    private Long   tenantId;

    /** Non-blocking warnings, e.g. "Document expired" — never a validation error. */
    private List<String> warnings;

    // ── Static factory ───────────────────────────────────────────────────────

    /** Legacy call sites with no permission/document context — no masking, no unified document fields. */
    public static ClientResponse from(Client client) {
        return from(client, null, true);
    }

    public static ClientResponse from(Client client, ClientIdentityDocument doc, boolean canViewFullDocument) {
        String firstName = "";
        String lastName = "";
        if (client.getName() != null) {
            String[] parts = client.getName().trim().split(" ", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }

        String rawDocumentNumber = doc != null ? doc.getDocumentNumber() : (client.getCin() != null ? client.getCin() : client.getPassportNumber());
        String masked = ClientService.maskDocumentNumber(rawDocumentNumber);

        List<String> warnings = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (doc != null && doc.getExpiryDate() != null) {
            if (doc.getExpiryDate().isBefore(today)) warnings.add("DOCUMENT_EXPIRED");
            else if (!doc.getExpiryDate().isAfter(today.plusDays(30))) warnings.add("DOCUMENT_EXPIRING_SOON");
        }
        if (client.getDrivingLicenseExpiry() != null) {
            if (client.getDrivingLicenseExpiry().isBefore(today)) warnings.add("LICENSE_EXPIRED");
            else if (!client.getDrivingLicenseExpiry().isAfter(today.plusDays(30))) warnings.add("LICENSE_EXPIRING_SOON");
        }

        return ClientResponse.builder()
                .id(client.getId())
                .fullName(client.getName())
                .name(client.getName())
                .firstName(firstName)
                .lastName(lastName)
                .email(client.getEmail())
                .phone(client.getPhone())
                .secondaryPhone(client.getSecondaryPhone())
                .address(client.getAddress())
                .city(client.getCity())
                .country(client.getCountry())
                .postalCode(client.getPostalCode())
                .nationality(client.getNationality())
                .gender(client.getGender())
                .birthDate(client.getBirthDate())
                .cin(client.getCin())
                .passportNumber(client.getPassportNumber())
                .documentType(doc != null ? doc.getDocumentType() : (client.getCin() != null ? DocumentType.CIN
                        : client.getPassportNumber() != null ? DocumentType.PASSPORT : null))
                .documentNumber(canViewFullDocument ? rawDocumentNumber : null)
                .documentMasked(masked)
                .documentIssuingCountry(doc != null ? doc.getIssuingCountry() : null)
                .documentIssuingAuthority(doc != null ? doc.getIssuingAuthority() : null)
                .documentIssueDate(doc != null ? doc.getIssueDate() : null)
                .documentExpiryDate(doc != null ? doc.getExpiryDate() : null)
                .drivingLicense(client.getDrivingLicense())
                .drivingLicenseIssue(client.getDrivingLicenseIssue())
                .drivingLicenseExpiry(client.getDrivingLicenseExpiry())
                .drivingLicenseCategory(client.getDrivingLicenseCategory())
                .drivingLicenseCountry(client.getDrivingLicenseCountry())
                .emergencyContactName(client.getEmergencyContactName())
                .emergencyContactPhone(client.getEmergencyContactPhone())
                .companyName(client.getCompanyName())
                .notes(client.getNotes())
                .tenantId(client.getTenant() != null ? client.getTenant().getId() : null)
                .warnings(warnings)
                .build();
    }
}
