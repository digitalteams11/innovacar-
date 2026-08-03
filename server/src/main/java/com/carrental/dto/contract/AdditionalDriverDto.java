package com.carrental.dto.contract;

import com.carrental.entity.SignatureStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * General-purpose driver DTO — used in contract list/detail views. Never
 * carries tokenHash or the full signatureData payload; see
 * {@link AdditionalDriverSignatureView} for the signature-image detail view.
 */
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

    private String email;
    private Boolean signatureRequired;
    private SignatureStatus signatureStatus;
    private LocalDateTime linkSentAt;
    private LocalDateTime openedAt;
    private LocalDateTime signedAt;
    private LocalDateTime declinedAt;

    /**
     * @see com.carrental.dto.contract.AdditionalDriverSignatureView
     */
}
