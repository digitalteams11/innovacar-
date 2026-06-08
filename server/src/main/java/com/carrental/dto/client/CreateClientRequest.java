package com.carrental.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/clients} — create a client.
 */
@Data
public class CreateClientRequest {

    @NotBlank(message = "Name is required")
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
