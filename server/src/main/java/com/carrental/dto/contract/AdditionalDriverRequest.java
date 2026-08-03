package com.carrental.dto.contract;

import lombok.Data;

import java.time.LocalDate;

/** Request body for creating/updating an additional driver (task 9's authenticated CRUD endpoints). */
@Data
public class AdditionalDriverRequest {
    private String fullName;
    private String cin;
    private String passportNumber;
    private String driverLicenseNumber;
    private String nationality;
    private String address;
    private String phone;
    private LocalDate birthDate;
    private String email;
    private Boolean signatureRequired;

    /** Explicit override — without it, a driver duplicating the main client's own license/phone is rejected. */
    private Boolean allowDuplicateOfMainClient;
}
