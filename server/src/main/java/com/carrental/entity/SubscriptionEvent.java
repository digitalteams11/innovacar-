package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Records every processed Whop webhook event for idempotency.
 * WhopWebhookController inserts a row here before processing and skips
 * any event whose whopEventId already exists.
 */
@Entity
@Table(name = "subscription_events",
       uniqueConstraints = @UniqueConstraint(name = "uq_whop_event_id", columnNames = "whop_event_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "whop_event_id", nullable = false, length = 255)
    private String whopEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "membership_id", length = 255)
    private String membershipId;

    @Column(name = "plan_code", length = 100)
    private String planCode;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) processedAt = OffsetDateTime.now();
    }
}
