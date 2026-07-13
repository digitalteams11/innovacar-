package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * @deprecated Superseded by {@link AiUsageLog}, written by {@code AiGatewayService}.
 * Retained only for historical read access to {@code ai_audit_logs_legacy} —
 * no new code writes to this table after the V51 migration renamed it.
 */
@Deprecated
@Entity
@Table(name = "ai_audit_logs_legacy", indexes = {
        @Index(name = "idx_ai_audit_tenant", columnList = "agency_id"),
        @Index(name = "idx_ai_audit_user", columnList = "user_id"),
        @Index(name = "idx_ai_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "agency_id")
    private Long agencyId;

    @Column(name = "role", length = 40)
    private String role;

    /** e.g. CHAT, REPORT, TRANSLATION, SUPPORT_SUGGESTION, AUTOMATION, GUIDE */
    @Column(name = "feature", length = 60, nullable = false)
    private String feature;

    /** Safe, non-sensitive category — never the raw prompt text. */
    @Column(name = "prompt_category", length = 120)
    private String promptCategory;

    @Column(name = "model", length = 80)
    private String model;

    @Column(name = "input_tokens_estimate")
    private Integer inputTokensEstimate;

    @Column(name = "output_tokens_estimate")
    private Integer outputTokensEstimate;

    /** SUCCESS, FAILED, BLOCKED, RATE_LIMITED */
    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void preventModification() {
        throw new IllegalStateException("AI audit records are append-only");
    }
}
