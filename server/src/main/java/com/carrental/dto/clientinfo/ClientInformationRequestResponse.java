package com.carrental.dto.clientinfo;

import com.carrental.entity.ClientInfoRequestStatus;
import com.carrental.entity.ClientInformationRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Admin-facing view of a request — the review-queue list/detail response. */
@Data
@Builder
public class ClientInformationRequestResponse {
    private Long id;
    private String temporaryName;
    private String phone;
    private String email;
    private String preferredLanguage;
    private ClientInfoRequestStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime revokedAt;
    private Long contractId;
    private Long approvedClientId;
    private LocalDateTime createdAt;

    /** Only populated once, in the response to the create call — never persisted, never retrievable again. */
    private String secureLink;

    /** The client's submission, once SUBMITTED — null before that. */
    private ClientInformationSubmitRequest submission;

    /** Existing clients that plausibly match the submission (phone/email/document number) — spec section 13. */
    private List<ClientMatchSummary> potentialDuplicates;

    @Data
    @Builder
    public static class ClientMatchSummary {
        private Long clientId;
        private String name;
        private String phone;
        private String email;
        private String matchedOn;
    }

    public static ClientInformationRequestResponse from(ClientInformationRequest r) {
        return ClientInformationRequestResponse.builder()
                .id(r.getId())
                .temporaryName(r.getTemporaryName())
                .phone(r.getPhone())
                .email(r.getEmail())
                .preferredLanguage(r.getPreferredLanguage())
                .status(r.getStatus())
                .expiresAt(r.getExpiresAt())
                .submittedAt(r.getSubmittedAt())
                .approvedAt(r.getApprovedAt())
                .revokedAt(r.getRevokedAt())
                .contractId(r.getContractId())
                .approvedClientId(r.getApprovedClientId())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
