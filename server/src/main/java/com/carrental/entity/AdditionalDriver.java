package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Additional driver information linked to a rental contract, plus its own
 * independent digital-signing workflow (see AdditionalDriverSigningService).
 * Signature fields live directly on this row — same flat-column convention
 * used by Contract's client/agency/employee signature columns — rather than
 * a separate generic signature entity.
 */
@Entity
@Table(name = "contract_additional_drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdditionalDriver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "cin", length = 50)
    private String cin;

    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "driver_license_number", length = 50)
    private String driverLicenseNumber;

    @Column(name = "nationality", length = 50)
    private String nationality;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "email", length = 150)
    private String email;

    // ── Signature workflow ───────────────────────────────────────────────────

    @Builder.Default
    @Column(name = "signature_required", nullable = false)
    private boolean signatureRequired = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "signature_status", nullable = false, length = 20)
    private SignatureStatus signatureStatus = SignatureStatus.PENDING;

    @Column(name = "signature_data", columnDefinition = "TEXT")
    private String signatureData;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "signed_ip", length = 50)
    private String signedIp;

    @Column(name = "signed_user_agent", length = 255)
    private String signedUserAgent;

    @Column(name = "declaration_version", length = 20)
    private String declarationVersion;

    /** JSON map, e.g. {"identityCorrect":true,...} — serialized/deserialized by the signing service. */
    @Column(name = "declarations_accepted", columnDefinition = "TEXT")
    private String declarationsAccepted;

    @Column(name = "token_hash", length = 128)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "token_revoked_at")
    private LocalDateTime tokenRevokedAt;

    @Column(name = "link_sent_at")
    private LocalDateTime linkSentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "decline_reason", length = 255)
    private String declineReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Link ─────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
