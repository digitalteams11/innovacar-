package com.carrental.dto.contract;

import com.carrental.entity.SignatureStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Driver-detail-only view for the admin "View signature" action — the one
 * place the full signature image is exposed, kept out of the lean
 * general-purpose {@link AdditionalDriverDto} used in list views.
 */
@Data
@Builder
public class AdditionalDriverSignatureView {
    private Long id;
    private String fullName;
    private SignatureStatus signatureStatus;
    private String signatureData;
    private LocalDateTime signedAt;
    private String signedIp;
    private String signedUserAgent;
    private String declarationVersion;
    private String declarationsAccepted;
    private LocalDateTime declinedAt;
    private String declineReason;
}
