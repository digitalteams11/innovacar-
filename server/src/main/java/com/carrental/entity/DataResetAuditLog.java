package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit trail for every Super Admin destructive data-reset attempt — written
 * before the action runs (status STARTED) and updated with the outcome
 * immediately after, so a crash mid-reset still leaves a record of what was
 * attempted.
 */
@Entity
@Table(name = "data_reset_audit_logs", indexes = {
        @Index(name = "idx_data_reset_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_data_reset_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataResetAuditLog {

    public enum Status { STARTED, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "tenant_name", length = 200)
    private String tenantName;

    @Column(name = "client_id")
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "performed_by_id")
    private Long performedById;

    @Column(name = "performed_by_email", length = 200)
    private String performedByEmail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "request_summary", length = 2000)
    private String requestSummary;

    @Column(name = "result_summary", length = 2000)
    private String resultSummary;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.STARTED;
    }
}
