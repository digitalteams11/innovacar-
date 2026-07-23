package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single identity document (CIN, passport, residence permit, or other)
 * belonging to a client. A client normally has exactly one primary document
 * at a time ({@code isPrimary = true}); non-primary rows are historical
 * (e.g. a renewed passport) and are excluded from the tenant-scoped
 * uniqueness check on {@code documentNumber}.
 */
@Entity
@Table(
    name = "client_identity_documents",
    indexes = {
        @Index(name = "idx_client_identity_documents_tenant", columnList = "tenant_id"),
        @Index(name = "idx_client_identity_documents_client", columnList = "client_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientIdentityDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 64)
    private String documentNumber;

    @Column(name = "issuing_country", length = 100)
    private String issuingCountry;

    @Column(name = "issuing_authority", length = 150)
    private String issuingAuthority;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "front_file_id")
    private String frontFileId;

    @Column(name = "back_file_id")
    private String backFileId;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = true;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (isPrimary == null) isPrimary = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
