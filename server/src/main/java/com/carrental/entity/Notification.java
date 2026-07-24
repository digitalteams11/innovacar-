package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_tenant", columnList = "tenant_id"),
    @Index(name = "idx_notification_tenant_read", columnList = "tenant_id, read"),
    @Index(name = "idx_notification_tenant_type", columnList = "tenant_id, type"),
    @Index(name = "idx_notification_entity", columnList = "tenant_id, entity_type, entity_id"),
    @Index(name = "idx_notification_created_at", columnList = "tenant_id, created_at")
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
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.INFO;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Module module;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "action_url", length = 255)
    private String actionUrl;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (read == null) read = false;
        if (severity == null) severity = Severity.INFO;
    }

    // ── Severity ──────────────────────────────────────────────

    public enum Severity {
        INFO, SUCCESS, WARNING, ERROR, CRITICAL
    }

    // ── Module ────────────────────────────────────────────────

    public enum Module {
        CONTRACTS, RESERVATIONS, VEHICLES, MAINTENANCE,
        PAYMENTS, GPS, SECURITY, SUBSCRIPTION, SYSTEM, EMPLOYEES
    }

    // ── Type ──────────────────────────────────────────────────

    public enum NotificationType {
        // Legacy generic types (keep for backward compat)
        SUCCESS, INFORMATION, WARNING, ERROR,
        // Contracts
        CONTRACT_CREATED, CONTRACT_SIGNED_AGENCY, QR_GENERATED,
        CLIENT_OPENED_CONTRACT, CLIENT_SIGNED_CONTRACT, CONTRACT_FULLY_SIGNED,
        PDF_GENERATED, CONTRACT_FINALIZED,
        CONTRACT_QR_GENERATED, CONTRACT_CLIENT_SIGNED, CONTRACT_AGENCY_SIGNED,
        CONTRACT_ACTIVATED, CONTRACT_COMPLETED, CONTRACT_CANCELLED,
        // Reservations
        RESERVATION_CREATED, RESERVATION_CONFIRMED, RESERVATION_CANCELLED,
        RESERVATION_STARTING_SOON, RESERVATION_ENDING_SOON, RESERVATION_OVERDUE,
        // Vehicles
        VEHICLE_CREATED, VEHICLE_STATUS_CHANGED, VEHICLE_AVAILABLE,
        VEHICLE_RESERVED, VEHICLE_RENTED, VEHICLE_MAINTENANCE,
        VEHICLE_DOCUMENT_EXPIRING, VEHICLE_INSURANCE_EXPIRING, VEHICLE_TECHNICAL_VISIT_EXPIRING,
        // Maintenance
        MAINTENANCE_CREATED, MAINTENANCE_STARTED, MAINTENANCE_COMPLETED,
        MAINTENANCE_CANCELLED, MAINTENANCE_DUE_SOON, MAINTENANCE_OVERDUE,
        MAINTENANCE_STATUS_UPDATED, VEHICLE_BLOCKED_BY_MAINTENANCE,
        // Payments
        PAYMENT_RECEIVED, PAYMENT_FAILED, PAYMENT_PARTIAL,
        INVOICE_CREATED, INVOICE_OVERDUE, CLIENT_BALANCE_DUE,
        // Subscriptions
        TRIAL_STARTED, TRIAL_ENDING_SOON, TRIAL_EXPIRED,
        SUBSCRIPTION_ACTIVATED, SUBSCRIPTION_RENEWED, SUBSCRIPTION_PAYMENT_FAILED,
        SUBSCRIPTION_CANCELLED, PLAN_UPGRADED, PLAN_DOWNGRADED,
        SUBSCRIPTION_EXPIRES_SOON,
        // GPS
        GPS_CONNECTED, GPS_DISCONNECTED, GPS_DEVICE_ONLINE, GPS_DEVICE_OFFLINE,
        GPS_VEHICLE_MOVED, GPS_VEHICLE_LEFT_CITY, GPS_SPEED_ALERT, GPS_GEOFENCE_EXIT,
        // Employees
        EMPLOYEE_CREATED, EMPLOYEE_DISABLED, EMPLOYEE_LOGIN, EMPLOYEE_PERMISSION_CHANGED,
        // Security
        LOGIN_FROM_NEW_DEVICE, PASSWORD_CHANGED, TWO_FACTOR_ENABLED, TWO_FACTOR_DISABLED,
        EMAIL_VERIFIED, SUSPICIOUS_LOGIN_ATTEMPT,
        // System
        BACKUP_COMPLETED, BACKUP_FAILED, SMTP_TEST_SUCCESS, SMTP_TEST_FAILED,
        AI_QUOTA_EXCEEDED, FEATURE_LIMIT_REACHED,
        // Legacy
        ACCOUNT_INACTIVE, LOW_USAGE, UPGRADE_SUGGESTION,
        // Support Center
        SUPPORT_TICKET_CREATED, SUPPORT_TICKET_REPLY, SUPPORT_TICKET_STATUS_CHANGED,
        // Client information requests (public self-fill form)
        CLIENT_INFORMATION_SUBMITTED
    }
}
