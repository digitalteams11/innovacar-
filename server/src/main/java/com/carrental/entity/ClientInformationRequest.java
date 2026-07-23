package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A "client self-fill information" request: the agency sends a secure link
 * (never the raw token itself — only its SHA-256 hash is stored) so a client
 * can submit their own contact/identity/license info without an Innovacar
 * account, for the agency to review and approve before it ever touches a
 * real Client record or contract.
 *
 * MVP scope (see ClientInformationRequestService) — deliberately smaller
 * than a full request lifecycle: no OPENED tracking, no server-side draft
 * autosave, no CORRECTION_REQUESTED round-trip, one submission only. The
 * submitted payload is stored as validated, structured JSON (never raw
 * client-controlled HTML/script — see ClientInformationSubmitRequest) rather
 * than normalized columns, since it's provisional/unverified until approved;
 * approval is what promotes it into real Client (+ ClientIdentityDocument)
 * rows.
 */
@Entity
@Table(
    name = "client_information_requests",
    indexes = {
        @Index(name = "idx_client_info_requests_tenant", columnList = "tenant_id"),
        @Index(name = "idx_client_info_requests_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_client_info_requests_contract", columnList = "contract_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientInformationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** SHA-256 hex digest of the raw token — the raw token itself is never persisted. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "temporary_name", length = 150)
    private String temporaryName;

    @Column(length = 50)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientInfoRequestStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** Optional contract draft this request feeds into (see section 16/17 of the spec) — MVP: contract flow only, no reservation flow yet. */
    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "approved_client_id")
    private Long approvedClientId;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    /**
     * Validated, structured JSON of the client's submission (personal info,
     * one identity document, address, driver license). Never rendered as
     * raw HTML anywhere; parsed into a typed DTO on read. Null until SUBMITTED.
     */
    @Column(name = "submission_payload", columnDefinition = "TEXT")
    private String submissionPayload;

    @Column(name = "privacy_accepted_at")
    private LocalDateTime privacyAcceptedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = ClientInfoRequestStatus.SENT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
