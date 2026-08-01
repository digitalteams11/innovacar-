package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records that a given user dismissed a given announcement, so the dismissal
 * survives across sessions/devices instead of living only in
 * sessionStorage — see AnnouncementBanner.tsx and its cooldown-days check
 * against {@link #getDismissedAt()}.
 */
@Entity
@Table(name = "announcement_dismissals", uniqueConstraints =
    @UniqueConstraint(columnNames = {"announcement_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dismissed_at", nullable = false)
    private LocalDateTime dismissedAt;

    @PrePersist
    protected void onCreate() {
        dismissedAt = LocalDateTime.now();
    }
}
