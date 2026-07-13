package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One row per legacy-to-generic-provider migration event. Append-only audit trail. */
@Entity
@Table(name = "ai_migration_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMigrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_provider", length = 40)
    private String sourceProvider;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
