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

    /** Ticket subject/title */
    @Column(nullable = false)
    private String subject;

    /** Ticket description */
    @Column(length = 2000)
    private String description;

    /** Ticket status: OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED */
    @Column(nullable = false)
    private String status;

    /** Priority: LOW, MEDIUM, HIGH, CRITICAL */
    @Column(nullable = false)
    private String priority;

    /** Category: BILLING, TECHNICAL, GPS, ACCOUNT, FEATURE_REQUEST, OTHER */
    @Column
    private String category;

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
        if (status == null) status = "OPEN";
        if (priority == null) priority = "MEDIUM";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
