package com.carrental.dto.contract;

import lombok.Data;

/**
 * Request body for submitting a digital signature on a contract.
 */
@Data
public class ContractSignatureRequest {

    private String signatureData;

    private SignerType signerType;

    private SignatureType signatureType;

    private Boolean termsAccepted;

    private String signedBy;

    private String deviceInfo;

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

    public enum SignatureType {
        AGENCY,
        CLIENT,
        EMPLOYEE,
        AGENCY_SIGNATURE,
        CLIENT_SIGNATURE,
        EMPLOYEE_SIGNATURE
    }

    public SignerType getResolvedSignerType() {
        if (signerType != null) return signerType;
        if (signatureType == null) return null;
        return switch (signatureType) {
            case AGENCY, AGENCY_SIGNATURE -> SignerType.OWNER;
            case CLIENT, CLIENT_SIGNATURE -> SignerType.CLIENT;
            case EMPLOYEE, EMPLOYEE_SIGNATURE -> SignerType.EMPLOYEE;
        };
    }
}
