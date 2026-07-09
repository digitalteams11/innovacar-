package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "onboarding_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private boolean welcomeDismissed;

    @Column(nullable = false)
    private boolean wizardSkipped;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private boolean tourCompleted;

    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
        if (completed && completedAt == null) completedAt = updatedAt;
    }
}
