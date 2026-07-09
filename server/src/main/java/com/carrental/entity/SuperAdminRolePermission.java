package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

/** One (role, permission) toggle — global, not tenant-scoped. */
@Entity
@Table(name = "super_admin_role_permissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_super_admin_role_permission", columnNames = {"role_id", "permission_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private SuperAdminRole role;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(nullable = false)
    private Boolean enabled;
}
