package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single Windows/Electron installer build that Super Admin can publish for
 * agencies to download. Only {@link Status#PUBLISHED} releases are ever
 * exposed by the public read endpoint — see PublicDesktopReleaseController.
 */
@Entity
@Table(name = "desktop_releases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesktopRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Architecture architecture;

    /** Display version, e.g. "1.2.0" — may include a build suffix. */
    @Column(nullable = false, length = 50)
    private String version;

    /** Strict semver (MAJOR.MINOR.PATCH) used for comparisons/ordering. */
    @Column(name = "semantic_version", nullable = false, length = 50)
    private String semanticVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "download_url", nullable = false, length = 1000)
    private String downloadUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(length = 64)
    private String sha256;

    @Column(name = "minimum_os", length = 100)
    private String minimumOs;

    @Column(name = "mandatory_update", nullable = false)
    private boolean mandatoryUpdate;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "release_notes_en", columnDefinition = "TEXT")
    private String releaseNotesEn;

    @Column(name = "release_notes_fr", columnDefinition = "TEXT")
    private String releaseNotesFr;

    @Column(name = "release_notes_ar", columnDefinition = "TEXT")
    private String releaseNotesAr;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (channel == null) channel = Channel.STABLE;
        if (status == null) status = Status.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Platform { WINDOWS }
    public enum Architecture { X64, ARM64 }
    public enum Channel { STABLE, BETA }
    public enum Status { DRAFT, PUBLISHED, DEPRECATED, WITHDRAWN }
}
