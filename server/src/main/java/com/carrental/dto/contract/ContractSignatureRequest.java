package com.carrental.dto.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for submitting a digital signature on a contract.
 */
@Data
public class ContractSignatureRequest {

    @NotBlank(message = "Signature data is required")
    private String signatureData;

    @NotNull(message = "Signer type is required")
    private SignerType signerType;

    private Boolean termsAccepted;

    /** Client IP address for audit trail */
    private String ipAddress;

    /** Client user agent for audit trail */
    private String userAgent;

    /**
     * Who is signing the contract.
     */
    public enum SignerType {
        CLIENT,
        OWNER,
        EMPLOYEE
    }
}
