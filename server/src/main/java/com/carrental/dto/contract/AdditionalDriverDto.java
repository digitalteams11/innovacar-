package com.carrental.dto.contract;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class AdditionalDriverDto {

    private Long id;
    private String fullName;
    private String cin;
    private String passportNumber;
    private String driverLicenseNumber;
    private String nationality;
    private String address;
    private String phone;
    private LocalDate birthDate;
}
