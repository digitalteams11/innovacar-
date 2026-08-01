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

    /** What this announcement is about — drives client-side rendering (icon, CTA) and eligibility rules for the desktop-promotion types. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    /** Restricts a DESKTOP_* announcement to one client platform (e.g. only show to Windows web users). Null = no platform restriction. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Platform platform;

    /** Release version this announcement refers to (e.g. NEW_MAJOR_DESKTOP_VERSION) — lets the client decide whether a new dismissal cycle is warranted for a newer version. */
    @Column(length = 50)
    private String version;

    @Column(nullable = false)
    private boolean dismissible;

    /** Days a user's dismissal suppresses this announcement before it's eligible to show again. */
    @Column(name = "cooldown_days", nullable = false)
    private int cooldownDays;

    /** Where the announcement's primary action navigates (e.g. /desktop-app). */
    @Column(name = "action_url", length = 1000)
    private String actionUrl;

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
        if (type == null) type = Type.GENERIC;
        if (cooldownDays <= 0) cooldownDays = 30;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Audience { ALL, SELECTED_AGENCIES, PLAN, ROLE }
    public enum Priority { LOW, NORMAL, HIGH, CRITICAL }
    public enum Type { GENERIC, DESKTOP_AVAILABLE, NEW_MAJOR_DESKTOP_VERSION, DESKTOP_SECURITY_UPDATE, DESKTOP_MAINTENANCE, DESKTOP_COMING_SOON }
    public enum Platform { WINDOWS, MAC, LINUX }
}
