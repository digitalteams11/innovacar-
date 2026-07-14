package com.carrental.legal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One versioned snapshot of a legal document's content in one language.
 * Exactly one version per (documentType, locale) may hold
 * {@link LegalDocumentStatus#PUBLISHED} at a time — enforced in
 * {@code LegalVersionService}, not at the DB constraint level, so publishing
 * can atomically archive the previous version in the same transaction.
 */
@Entity
@Table(
    name = "legal_document_versions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"document_type", "locale", "version_number"}),
    indexes = {
        @Index(name = "idx_legal_doc_type_locale_status", columnList = "document_type, locale, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalDocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 60)
    private LegalDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "locale", nullable = false, length = 5)
    private LegalLocale locale;

    /** Sequential per (documentType, locale), starting at 1. */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    /** Rendered document body (HTML, sanitized on the way in). */
    @Column(name = "content_html", nullable = false, columnDefinition = "TEXT")
    private String contentHtml;

    /** Human-readable summary of what changed vs. the previous version — shown in re-acceptance prompts. */
    @Column(name = "summary_of_changes", columnDefinition = "TEXT")
    private String summaryOfChanges;

    /**
     * When true, publishing this version invalidates any prior acceptance of
     * an earlier version of the same (documentType, locale) — affected users
     * must re-accept before continuing to use gated features.
     */
    @Column(name = "material", nullable = false)
    @Builder.Default
    private boolean material = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LegalDocumentStatus status = LegalDocumentStatus.DRAFT;

    /** Legal effective date shown to users (may differ from publishedAt, the technical publish timestamp). */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_by_email", length = 200)
    private String createdByEmail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = LegalDocumentStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
