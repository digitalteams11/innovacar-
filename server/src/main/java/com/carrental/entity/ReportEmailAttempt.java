package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One row per send/resend attempt of a report email — drives retry + idempotency independent of Report.emailStatus. */
@Entity
@Table(name = "report_email_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportEmailAttempt {

    public static final String TRIGGERED_BY_SYSTEM = "SYSTEM";
    public static final String TRIGGERED_BY_MANUAL_SEND = "MANUAL_SEND";
    public static final String TRIGGERED_BY_MANUAL_RESEND = "MANUAL_RESEND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "triggered_by", nullable = false, length = 30)
    private String triggeredBy;

    @Column(name = "recipient_emails", nullable = false, length = 1000)
    private String recipientEmails;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @PrePersist
    protected void onCreate() {
        if (attemptedAt == null) attemptedAt = LocalDateTime.now();
        if (triggeredBy == null) triggeredBy = TRIGGERED_BY_SYSTEM;
    }
}
