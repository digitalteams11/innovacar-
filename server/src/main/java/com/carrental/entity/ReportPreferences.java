package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Per-tenant reporting settings (spec section 18). One row per tenant. */
@Entity
@Table(name = "report_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "report_enabled", nullable = false)
    private boolean reportEnabled;

    @Column(name = "monthly_report_enabled", nullable = false)
    private boolean monthlyReportEnabled;

    @Column(name = "yearly_report_enabled", nullable = false)
    private boolean yearlyReportEnabled;

    @Column(name = "report_language", nullable = false, length = 5)
    private String reportLanguage;

    @Column(length = 60)
    private String timezone;

    @Column(name = "primary_recipient_email")
    private String primaryRecipientEmail;

    @Column(name = "additional_recipient_emails", length = 1000)
    private String additionalRecipientEmails;

    @Column(name = "include_ai_summary", nullable = false)
    private boolean includeAiSummary;

    @Column(name = "include_client_debt_detail", nullable = false)
    private boolean includeClientDebtDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (reportLanguage == null) reportLanguage = "fr";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
