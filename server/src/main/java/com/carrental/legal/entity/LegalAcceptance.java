package com.carrental.legal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit record: "this user accepted this exact document version at
 * this time". Never updated after insert — a new acceptance of a newer
 * version is always a new row, so the full consent history is preserved.
 */
@Entity
@Table(
    name = "legal_acceptances",
    indexes = {
        @Index(name = "idx_legal_acceptance_user_type", columnList = "user_id, document_type"),
        @Index(name = "idx_legal_acceptance_tenant", columnList = "tenant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Denormalized for reporting ("which agencies still have pending re-acceptances") without joining through users. */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 60)
    private LegalDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "locale", nullable = false, length = 5)
    private LegalLocale locale;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Where the acceptance was captured: SIGNUP, FORCED_REACCEPTANCE, SETTINGS_PAGE, ... */
    @Column(name = "capture_context", length = 40)
    private String captureContext;

    @PrePersist
    protected void onCreate() {
        if (acceptedAt == null) acceptedAt = LocalDateTime.now();
    }
}
