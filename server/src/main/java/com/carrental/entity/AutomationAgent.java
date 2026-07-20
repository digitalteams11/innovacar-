package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-tenant state for one automation agent (or a single platform-wide row when
 * {@code tenantId} is null — e.g. the Subscription &amp; Trial Agent and Backup
 * Verification Agent run once for the whole platform, not once per tenant; see
 * each agent's own class javadoc for which shape it uses).
 */
@Entity
@Table(name = "automation_agents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "agent_key", nullable = false, length = 60)
    private String agentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    @Builder.Default
    @Column(name = "success_count", nullable = false)
    private Long successCount = 0L;

    @Builder.Default
    @Column(name = "failure_count", nullable = false)
    private Long failureCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status { ACTIVE, PAUSED, DEGRADED, ERROR, DISABLED, REQUIRES_CONFIGURATION }
}
