package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Append-only record of every role/permission change — who changed what, on
 * which role or user, from what value to what value, and from where.
 * Follows this codebase's existing per-domain-audit-table convention
 * ({@code ContractAuditLog}, {@code AiAuditLog}, {@code DataResetAuditLog})
 * rather than the single generic {@code AuditLog} table.
 */
@Entity
@Table(name = "role_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    /** ROLE_CREATED | ROLE_UPDATED | ROLE_DELETED | ROLE_PERMISSION_CHANGED | USER_ROLE_CHANGED | USER_OVERRIDE_ADDED | USER_OVERRIDE_REMOVED */
    @Column(nullable = false, length = 40)
    private String action;

    /** SYSTEM_ROLE | CUSTOM_ROLE */
    @Column(name = "role_type", length = 20)
    private String roleType;

    @Column(name = "role_code", length = 60)
    private String roleCode;

    @Column(name = "custom_role_id")
    private Long customRoleId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "permission_code", length = 100)
    private String permissionCode;

    @Column(name = "old_value", length = 60)
    private String oldValue;

    @Column(name = "new_value", length = 60)
    private String newValue;

    @Column(length = 500)
    private String reason;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
