package com.carrental.dto.clientinfo;

import com.carrental.entity.ClientInfoRequestStatus;
import com.carrental.entity.ClientInformationRequest;
import com.carrental.entity.DeliveryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Admin-facing view of a request — the review-queue list/detail response, and the create/resend result. */
@Data
@Builder
public class ClientInformationRequestResponse {
    private Long id;
    /** Alias of {@link #id} — matches the {@code requestId} field name used in the API spec response shape. */
    private Long requestId;
    private Long clientId;
    private String temporaryName;
    private String phone;
    private String email;
    private String preferredLanguage;
    private ClientInfoRequestStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime sentAt;
    private LocalDateTime openedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime revokedAt;
    private Long contractId;
    private Long approvedClientId;
    private LocalDateTime createdAt;

    /** Only populated once, in the response to the create/resend call — never persisted, never retrievable again. */
    private String secureLink;
    /** Alias of {@link #secureLink} — matches the {@code publicUrl} field name used in the API spec response shape. */
    private String publicUrl;

    private List<String> deliveryChannels;
    private DeliveryResult emailResult;
    private DeliveryResult whatsappResult;

    /** The client's submission, once SUBMITTED — null before that. */
    private ClientInformationSubmitRequest submission;

    /** Existing clients that plausibly match the submission (phone/email/document number). */
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

    /** Per-channel delivery outcome — matches the {email:{attempted,sent,message}} shape in the API spec. */
    @Data
    @Builder
    public static class DeliveryResult {
        private boolean attempted;
        private boolean sent;
        private String message;
        private DeliveryStatus status;
    }

    public static ClientInformationRequestResponse from(ClientInformationRequest r) {
        return ClientInformationRequestResponse.builder()
                .id(r.getId())
                .requestId(r.getId())
                .clientId(r.getClientId())
                .temporaryName(r.getTemporaryName())
                .phone(r.getPhone())
                .email(r.getEmail())
                .preferredLanguage(r.getPreferredLanguage())
                .status(r.getStatus())
                .expiresAt(r.getExpiresAt())
                .openedAt(r.getOpenedAt())
                .submittedAt(r.getSubmittedAt())
                .approvedAt(r.getApprovedAt())
                .rejectedAt(r.getRejectedAt())
                .revokedAt(r.getRevokedAt())
                .contractId(r.getContractId())
                .approvedClientId(r.getApprovedClientId())
                .createdAt(r.getCreatedAt())
                .deliveryChannels(r.getDeliveryChannels() != null
                        ? Arrays.asList(r.getDeliveryChannels().split(","))
                        : List.of())
                .emailResult(DeliveryResult.builder()
                        .attempted(r.getEmailDeliveryStatus() != DeliveryStatus.NOT_REQUESTED)
                        .sent(r.getEmailDeliveryStatus() == DeliveryStatus.SENT)
                        .status(r.getEmailDeliveryStatus())
                        .message(r.getEmailLastError())
                        .build())
                .whatsappResult(DeliveryResult.builder()
                        .attempted(r.getWhatsappDeliveryStatus() != DeliveryStatus.NOT_REQUESTED)
                        .sent(r.getWhatsappDeliveryStatus() == DeliveryStatus.SENT)
                        .status(r.getWhatsappDeliveryStatus())
                        .message(r.getWhatsappLastError())
                        .build())
                .build();
    }
}
