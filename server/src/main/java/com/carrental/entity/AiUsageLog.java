package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Replaces {@link AiAuditLog} as the write path for new AI calls. Append-only
 * — deliberately does not store raw prompts/responses, only safe metadata.
 */
@Entity
@Table(name = "ai_usage_logs", indexes = {
        @Index(name = "idx_ai_usage_agency", columnList = "agency_id"),
        @Index(name = "idx_ai_usage_user", columnList = "user_id"),
        @Index(name = "idx_ai_usage_created", columnList = "created_at"),
        @Index(name = "idx_ai_usage_provider", columnList = "ai_provider_id"),
        @Index(name = "idx_ai_usage_automation", columnList = "automation_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_provider_id")
    private Long providerId;

    @Column(name = "ai_model_id")
    private Long modelId;

    @Column(name = "automation_code", length = 60)
    private String automationCode;

    @Column(name = "agency_id")
    private Long agencyId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role", length = 40)
    private String role;

    @Column(name = "request_id", length = 60)
    private String requestId;

    /** SUCCESS, FAILED, BLOCKED, RATE_LIMITED */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "estimated_cost")
    private BigDecimal estimatedCost;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void preventModification() {
        throw new IllegalStateException("AI usage logs are append-only");
    }
}
