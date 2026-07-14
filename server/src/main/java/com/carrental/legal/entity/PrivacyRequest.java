package com.carrental.legal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A data-subject rights request (access, rectification, deletion, export,
 * objection, restriction). Fulfilment of DELETION/EXPORT is orchestrated by
 * {@code PrivacyRequestService} calling into existing account/data services;
 * this entity is the tracked, auditable record of the request lifecycle.
 */
@Entity
@Table(
    name = "privacy_requests",
    indexes = {
        @Index(name = "idx_privacy_request_user", columnList = "user_id"),
        @Index(name = "idx_privacy_request_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivacyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private PrivacyRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PrivacyRequestStatus status = PrivacyRequestStatus.PENDING;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = PrivacyRequestStatus.PENDING;
    }
}
