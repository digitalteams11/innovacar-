package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "email_type", length = 60)
    private String emailType;

    @Column(name = "template_id")
    private Long templateId;

    @Column
    private String templateName;

    @Column(nullable = false)
    private String recipient;

    @Column
    private String subject;

    @Column
    private String status; // SENT, FAILED, PENDING

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (sentAt == null && "SENT".equals(status)) {
            sentAt = LocalDateTime.now();
        }
    }

    // ── Well-known email type constants ───────────────────────────────────────

    public static final String TYPE_CONTRACT_SIGNED_CLIENT      = "CONTRACT_SIGNED_CLIENT";
    public static final String TYPE_CONTRACT_COMPLETED_CLIENT   = "CONTRACT_COMPLETED_CLIENT";
    public static final String TYPE_RESERVATION_CONFIRMATION    = "RESERVATION_CONFIRMATION_CLIENT";
    public static final String TYPE_SMTP_TEST                   = "SMTP_TEST";
    public static final String TYPE_CONTRACT_READY              = "CONTRACT_READY_CLIENT";
    public static final String TYPE_SUBSCRIPTION_CANCEL_SCHEDULED = "SUBSCRIPTION_CANCELLATION_SCHEDULED";
    public static final String TYPE_SUBSCRIPTION_CANCEL_UNDONE    = "SUBSCRIPTION_CANCELLATION_UNDONE";
    public static final String TYPE_SUBSCRIPTION_CANCELLED_FINAL  = "SUBSCRIPTION_CANCELLED_FINAL";
    public static final String TYPE_SUPPORT_TICKET_CREATED        = "SUPPORT_TICKET_CREATED";
    public static final String TYPE_SUPPORT_REPLY                 = "SUPPORT_REPLY";
    public static final String TYPE_CONTACT_FORM                  = "CONTACT_FORM";
}
