package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inspection_media", indexes = {
        @Index(name = "idx_inspection_media_inspection", columnList = "inspection_id"),
        @Index(name = "idx_inspection_media_token", columnList = "access_token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionMediaType type;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(length = 80)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "size_bytes", nullable = false)
    private Long size;

    @Column(name = "duration_seconds")
    private Integer duration;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "access_token", nullable = false, unique = true, length = 128)
    private String accessToken;

    /** False when superseded by a newer upload of the same label for the same inspection. */
    @Column(name = "active")
    private Boolean active;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
        if (active == null) active = true;
    }
}
