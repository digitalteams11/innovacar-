package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An explicit grant or deny of one permission code for one user, layered on
 * top of their role's normal permission set — lets an administrator give (or
 * take away) a single specific permission without editing the whole role's
 * matrix (spec: "Do not force the administrator to edit the entire role
 * matrix just to give one user one extra permission").
 *
 * <p>Evaluation order (see RolePermissionService#hasResolvedPermission):
 * an explicit DENY here always wins over the role's own grant; an explicit
 * GRANT here grants access even if the role itself denies it; if neither
 * exists, the role's own permission applies.
 */
@Entity
@Table(name = "user_permission_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermissionOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_type", nullable = false, length = 10)
    private PermissionOverrideType overrideType;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
