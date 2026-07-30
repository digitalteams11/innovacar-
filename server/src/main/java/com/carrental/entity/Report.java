package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Metadata for one generated financial/operational report. The PDF bytes are
 * never stored here — see ReportPdfStorage, which persists them on disk under
 * a path derived from {@link #fileStorageKey}. {@link #calculationSnapshot}
 * is the serialized {@code ReportDataset} JSON and is the source of truth for
 * re-download/audit even if the PDF file itself is ever lost.
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 10)
    private ReportType reportType;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(nullable = false, length = 5)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by", nullable = false, length = 30)
    private ReportGeneratedBy generatedBy;

    @Column(name = "generated_by_user_id")
    private Long generatedByUserId;

    @Column(name = "file_storage_key", length = 500)
    private String fileStorageKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(length = 128)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    private ReportEmailStatus emailStatus;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "recipient_emails", length = 1000)
    private String recipientEmails;

    @Column(name = "ai_summary_used", nullable = false)
    private boolean aiSummaryUsed;

    @Column(name = "calculation_version", nullable = false)
    private Integer calculationVersion;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "calculation_snapshot", columnDefinition = "TEXT")
    private String calculationSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = ReportStatus.PENDING;
        if (emailStatus == null) emailStatus = ReportEmailStatus.NOT_SENT;
        if (generatedBy == null) generatedBy = ReportGeneratedBy.SCHEDULER;
        if (calculationVersion == null) calculationVersion = 1;
        if (language == null) language = "fr";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
