package com.carrental.dto.client;

import com.carrental.entity.Client;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Read-only client projection returned by all client endpoints.
 */
@Data
@Builder
public class ClientResponse {

    private Long   id;
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
    private String cin;
    private String passportNumber;
    private String drivingLicense;
    private LocalDate drivingLicenseIssue;
    private LocalDate drivingLicenseExpiry;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String companyName;
    private String notes;
    private Long   tenantId;

    // ── Static factory ───────────────────────────────────────────────────────

    public static ClientResponse from(Client client) {
        String firstName = "";
        String lastName = "";
        if (client.getName() != null) {
            String[] parts = client.getName().trim().split(" ", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }
        return ClientResponse.builder()
                .id(client.getId())
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
                .drivingLicense(client.getDrivingLicense())
                .drivingLicenseIssue(client.getDrivingLicenseIssue())
                .drivingLicenseExpiry(client.getDrivingLicenseExpiry())
                .emergencyContactName(client.getEmergencyContactName())
                .emergencyContactPhone(client.getEmergencyContactPhone())
                .companyName(client.getCompanyName())
                .notes(client.getNotes())
                .tenantId(client.getTenant().getId())
                .build();
    }
}
