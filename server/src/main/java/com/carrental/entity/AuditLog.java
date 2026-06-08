package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Security and audit log for tracking platform activity.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Action type: LOGIN, LOGOUT, CREATE, UPDATE, DELETE, VIEW, EXPORT, SETTINGS_CHANGE */
    @Column(nullable = false)
    private String action;

    /** Entity type affected: USER, TENANT, VEHICLE, RESERVATION, etc. */
    @Column(name = "entity_type")
    private String entityType;

    /** Entity ID affected */
    @Column(name = "entity_id")
    private Long entityId;

    /** Description of the action */
    @Column(length = 1000)
    private String description;

    /** User who performed the action */
    @Column(name = "performed_by")
    private String performedBy;

    /** User ID who performed the action */
    @Column(name = "performed_by_id")
    private Long performedById;

    /** Tenant ID affected (if applicable) */
    @Column(name = "tenant_id")
    private Long tenantId;

    /** IP address of the request */
    @Column(name = "ip_address")
    private String ipAddress;

    /** User agent string */
    @Column(name = "user_agent")
    private String userAgent;

    /** Whether the action was successful */
    @Column(name = "is_success")
    private Boolean isSuccess;

    /** Error message if failed */
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isSuccess == null) isSuccess = true;
    }
}
