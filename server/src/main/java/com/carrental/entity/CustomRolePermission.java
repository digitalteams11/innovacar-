package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

/** One permission's enabled/disabled state within a {@link CustomRole}. */
@Entity
@Table(name = "custom_role_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomRolePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "custom_role_id", nullable = false)
    private CustomRole customRole;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;
}
