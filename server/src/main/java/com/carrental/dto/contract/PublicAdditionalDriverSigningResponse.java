package com.carrental.dto.contract;

import com.carrental.entity.SignatureStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Public (no-login) view for the additional-driver signing page. Never
 * includes payment/pricing fields, tokenHash, internal DB ids, or the full
 * license number (masked).
 */
@Data
@Builder
public class PublicAdditionalDriverSigningResponse {
    private String driverName;
    private String maskedLicense;
    private String contractNumber;
    private String agencyName;
    private String agencyLogo;
    private String vehicleSummary;
    private LocalDate rentalStartDate;
    private LocalDate rentalEndDate;
    private String mainClientName;
    private SignatureStatus signatureStatus;
    private String declarationVersion;
    private boolean contractCancelled;
}
