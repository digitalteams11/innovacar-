package com.carrental.legal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Current cookie-category preferences for one visitor. Works both
 * pre-authentication (keyed by {@code anonymousId}, a UUID the frontend
 * stores in a first-party cookie) and post-authentication (keyed by
 * {@code userId}, merged in from the anonymous record at login time).
 * "Necessary" cookies are never a stored choice — they are always on and
 * required for the app to function (session, CSRF, load balancing).
 */
@Entity
@Table(
    name = "cookie_consents",
    indexes = {
        @Index(name = "idx_cookie_consent_anonymous", columnList = "anonymous_id", unique = true),
        @Index(name = "idx_cookie_consent_user", columnList = "user_id", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookieConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Set once the visitor logs in; null for anonymous pre-login consent records. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    /** UUID string stored client-side before login; lets consent survive across an anonymous session. */
    @Column(name = "anonymous_id", length = 64)
    private String anonymousId;

    @Builder.Default
    @Column(name = "functional", nullable = false)
    private boolean functional = false;

    @Builder.Default
    @Column(name = "analytics", nullable = false)
    private boolean analytics = false;

    @Builder.Default
    @Column(name = "marketing", nullable = false)
    private boolean marketing = false;

    /** Version number of the COOKIE_POLICY the visitor's choice was made under. */
    @Column(name = "policy_version_number")
    private Integer policyVersionNumber;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
