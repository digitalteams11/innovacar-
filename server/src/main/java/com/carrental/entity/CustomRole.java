package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An agency-defined role layered alongside the system {@link Role} enum, not
 * replacing it — a user with a custom role keeps {@code role == Role.CUSTOM}
 * (so every existing {@code Role.X} comparison across the codebase still
 * resolves to something sane) and is additionally linked to one row here via
 * {@link User#getCustomRole()}.
 */
@Entity
@Table(name = "custom_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** Which system Role this was cloned from — powers the "reset to default" action. */
    @Column(name = "base_template", length = 30)
    private String baseTemplate;

    @Column(length = 20)
    private String color;

    @Column(length = 40)
    private String icon;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
