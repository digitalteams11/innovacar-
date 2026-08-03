package com.carrental.dto.contract;

import lombok.Data;

import java.util.Map;

@Data
public class AdditionalDriverSignatureSubmitRequest {

    /** e.g. {"identityCorrect":true,"responsibilityAccepted":true,...} — all must be true to submit. */
    private Map<String, Boolean> declarationsAccepted;

    /** Base64 PNG data URL from the signature pad. */
    private String signatureData;

    /** Optional — server also falls back to the User-Agent header if this is blank, same as ContractSignatureRequest. */
    private String deviceInfo;
}
