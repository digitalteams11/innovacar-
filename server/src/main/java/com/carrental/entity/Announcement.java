package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Platform-wide announcement broadcast by Super Admin to agencies. Delivery
 * is in-app only for now (the agency dashboard banner reads the active
 * announcement list); email/SMS/WhatsApp channels are recorded as the
 * requested channel but are not yet dispatched by a real provider, so they
 * are intentionally excluded from {@link #getChannels()} until that
 * integration exists — surfacing a real channel without real delivery would
 * be a fake-success result.
 */
@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Audience audience;

    /** CSV of tenant IDs (SELECTED_AGENCIES), a single plan code (PLAN), or a single role name (ROLE). Null for ALL. */
    @Column(name = "audience_value", length = 500)
    private String audienceValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (priority == null) priority = Priority.NORMAL;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Audience { ALL, SELECTED_AGENCIES, PLAN, ROLE }
    public enum Priority { LOW, NORMAL, HIGH, CRITICAL }
}
