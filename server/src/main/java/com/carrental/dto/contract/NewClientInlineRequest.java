package com.carrental.dto.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.time.LocalDate;

/**
 * Inline new-client payload accepted inside {@code POST /contracts/direct-create}.
 * Intentionally separate from {@link com.carrental.dto.client.CreateClientRequest}
 * so that the standalone client-creation endpoint keeps its own stricter validation
 * (address, city, country required) while inline creation only mandates the minimum.
 */
@Data
public class NewClientInlineRequest {

    @JsonAlias({"name", "clientName"})
    private String fullName;

    @JsonAlias({"phoneNumber", "telephone", "tel"})
    private String phone;

    @JsonAlias({"cinNumber", "cinPassport", "identityNumber"})
    private String cin;

    private String passportNumber;

    @JsonAlias({"drivingLicense", "driverLicenseNo", "licenseNumber", "numeroPermis", "permisNumber", "licenseNo"})
    private String driverLicenseNumber;

    private String email;

    private String address;

    @JsonAlias({"birthDate", "dob"})
    private LocalDate dateOfBirth;

    private String nationality;

    private String notes;
}
