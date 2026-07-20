package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One recorded execution of an automation agent — real history, never a fabricated row. */
@Entity
@Table(name = "automation_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for platform-wide agents (subscription/trial batch, backup verification). */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "agent_key", nullable = false, length = 60)
    private String agentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "sanitized_error_message", length = 1000)
    private String sanitizedErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Status { RUNNING, SUCCESS, PARTIAL_SUCCESS, FAILED }
}
