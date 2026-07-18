package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Support ticket raised by an agency or user.
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", unique = true)
    private String ticketNumber;

    /** Ticket subject/title */
    @Column(nullable = false)
    private String subject;

    /** Ticket description */
    @Column(length = 2000)
    private String description;

    /** Ticket status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** Priority */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    /** Category */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    /** The tenant that opened the ticket */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /** User who created the ticket */
    @Column(name = "created_by")
    private String createdBy;

    /** User email for contact */
    @Column(name = "contact_email")
    private String contactEmail;

    /** Assigned support agent */
    @Column(name = "assigned_to")
    private String assignedTo;

    /** Resolution notes */
    @Column(length = 2000)
    private String resolution;

    /** Routing channel: CONTACT, SUPPORT, TECHNICAL, BILLING, SECURITY */
    @Column(length = 20)
    private String channel;

    /** Resolved destination email this ticket was routed to at creation time */
    @Column(name = "destination_email")
    private String destinationEmail;

    /** Internal-notification email delivery status: PENDING, SENT, FAILED */
    @Column(name = "email_status", length = 20)
    private String emailStatus;

    /** Requester display name (set for anonymous/public submissions too) */
    @Column(name = "requester_name")
    private String requesterName;

    /** Requester email (set for anonymous/public submissions too) */
    @Column(name = "requester_email")
    private String requesterEmail;

    /** Requester phone, optional */
    @Column(name = "requester_phone")
    private String requesterPhone;

    @Column(name = "related_contract_id")
    private Long relatedContractId;

    @Column(name = "related_reservation_id")
    private Long relatedReservationId;

    @Column(name = "related_vehicle_id")
    private Long relatedVehicleId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = Status.OPEN;
        if (priority == null) priority = Priority.MEDIUM;
        if (emailStatus == null) emailStatus = "PENDING";
        if (ticketNumber == null) {
            ticketNumber = "RC-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * UNKNOWN is an explicit, self-documenting landing spot for legacy values
     * that don't map cleanly during the raw-String -> enum migration (V64) —
     * never silently coerced into a "normal" value.
     */
    public enum Status { OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED, UNKNOWN }

    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN }

    public enum Category { BILLING, TECHNICAL, GPS, ACCOUNT, FEATURE_REQUEST, OTHER, UNKNOWN }
}
