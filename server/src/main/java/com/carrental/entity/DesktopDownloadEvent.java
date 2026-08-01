package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A privacy-safe record of a desktop-installer download attempt — used for
 * adoption/analytics only, never as a proxy for "installed" (a download can
 * fail, be cancelled, or never be run). No IP address or device fingerprint
 * is stored here.
 */
@Entity
@Table(name = "desktop_download_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesktopDownloadEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id")
    private Long releaseId;

    @Column(length = 50)
    private String version;

    @Column(length = 20)
    private String platform;

    @Column(length = 20)
    private String architecture;

    /** Null for an anonymous (not-yet-authenticated / public site) download. */
    @Column(name = "agency_id")
    private Long agencyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Source { LANDING, DESKTOP_PAGE, DASHBOARD_BANNER, SETTINGS }
    public enum Status { STARTED, FAILED }
}
