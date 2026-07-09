package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A platform-level (Innovax staff) role — distinct from the agency-level
 * {@link Role} enum. Scoped globally (no tenant), since this governs what an
 * Innovax staff member can do inside the Super Admin control center.
 */
@Entity
@Table(name = "super_admin_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_super_admin_role_code", columnNames = {"code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 500)
    private String description;

    /** System-seeded roles (SUPER_OWNER, MANAGER, etc.) cannot be deleted or renamed. */
    @Column(name = "system_role", nullable = false)
    private Boolean systemRole;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (systemRole == null) systemRole = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
