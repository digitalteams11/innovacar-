package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * In-app notification for tenant users.
 * Created when contract events occur (signed, QR generated, etc.)
 * and pushed to clients via SSE.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_tenant", columnList = "tenant_id"),
    @Index(name = "idx_notification_tenant_read", columnList = "tenant_id, read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (read == null) read = false;
    }

    public enum NotificationType {
        SUCCESS,
        INFORMATION,
        WARNING,
        ERROR,
        CONTRACT_CREATED,
        CONTRACT_SIGNED_AGENCY,
        QR_GENERATED,
        CLIENT_OPENED_CONTRACT,
        CLIENT_SIGNED_CONTRACT,
        CONTRACT_FULLY_SIGNED,
        PDF_GENERATED,
        CONTRACT_FINALIZED,
        SUBSCRIPTION_EXPIRES_SOON,
        ACCOUNT_INACTIVE,
        LOW_USAGE,
        UPGRADE_SUGGESTION
    }
}
