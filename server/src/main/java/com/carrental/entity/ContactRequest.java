package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Public "Contact Us" submission — deliberately separate from {@link SupportTicket}.
 * Contact requests are always anonymous/public by construction (no tenant column),
 * and only become a ticket via an explicit Super Admin "convert to ticket" action.
 */
@Entity
@Table(name = "contact_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", unique = true)
    private String requestNumber;

    @Column(nullable = false)
    private String subject;

    @Column(length = 5000, nullable = false)
    private String message;

    /** GENERAL, SALES, PARTNERSHIP, PRESS, LEGAL, PRIVACY, SECURITY, OTHER */
    @Column(length = 30)
    private String category;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @Column(name = "requester_phone")
    private String requesterPhone;

    /** Resolved destination email this request was routed to at creation time */
    @Column(name = "destination_email")
    private String destinationEmail;

    /** Notification email delivery status: PENDING, SENT, FAILED */
    @Column(name = "email_status", length = 20)
    private String emailStatus;

    /** Lifecycle status: NEW, REVIEWING, REPLIED, CONVERTED, ARCHIVED */
    @Column(nullable = false, length = 20)
    private String status;

    /** Set when a Super Admin explicitly converts this request into a support ticket */
    @Column(name = "converted_ticket_id")
    private Long convertedTicketId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "NEW";
        if (emailStatus == null) emailStatus = "PENDING";
        if (requestNumber == null) {
            requestNumber = "CR-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
