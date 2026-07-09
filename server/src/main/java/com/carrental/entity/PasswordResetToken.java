package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores a 6-digit password reset code (hashed) and, after verification,
 * a short-lived reset-session token (hashed) that authorises setting a new password.
 *
 * <p>Status lifecycle: PENDING → USED (success) | EXPIRED (max attempts / timeout).
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Legacy field kept for backward compat — nullable in new code flow. */
    @Column(name = "token_hash", unique = true, length = 512)
    private String tokenHash;

    /** BCrypt hash of the 6-digit reset code. */
    @Column(name = "code_hash", length = 512)
    private String codeHash;

    /**
     * BCrypt hash of the short-lived session token issued after code verification.
     * Authorises the final reset-password call only.
     */
    @Column(name = "reset_session_token_hash", length = 512)
    private String resetSessionTokenHash;

    /** When the reset session token expires (10 minutes after code verification). */
    @Column(name = "reset_session_expires_at")
    private LocalDateTime resetSessionExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** When the 6-digit code expires (10 minutes after creation). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Legacy used flag — kept for backward compat; prefer {@code status}. */
    @Column(name = "used")
    private Boolean used;

    /** Number of failed code verification attempts. Max 5 before invalidation. */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    /** PENDING → USED (success) | EXPIRED (timeout or too many attempts). */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (used == null)     used = false;
        if (attempts == null) attempts = 0;
        if (status == null)   status = "PENDING";
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isResetSessionExpired() {
        return resetSessionExpiresAt == null
                || LocalDateTime.now().isAfter(resetSessionExpiresAt);
    }
}
