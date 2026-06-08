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

    private String cin;

    private String passportNumber;

    private String drivingLicense;

    private LocalDate drivingLicenseIssue;

    private LocalDate drivingLicenseExpiry;

    private String emergencyContactName;

    private String emergencyContactPhone;

    private String companyName;

    private String notes;
}
