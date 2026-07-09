package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One-time token for email verification.
 * Sent to users after registration to confirm their email address.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 512)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used")
    private Boolean used;

    /** Hashed 6-digit code for code-based email verification (null when link-based) */
    @Column(name = "verification_code_hash", length = 255)
    private String verificationCodeHash;

    /** Expiry for the code-based flow (10 minutes) */
    @Column(name = "verification_code_expires_at")
    private LocalDateTime verificationCodeExpiresAt;

    /** Failed verification attempts for the code (max 5) */
    @Column(name = "verification_code_attempts")
    private Integer verificationCodeAttempts;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (used == null) used = false;
        if (verificationCodeAttempts == null) verificationCodeAttempts = 0;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isCodeExpired() {
        return verificationCodeExpiresAt == null || LocalDateTime.now().isAfter(verificationCodeExpiresAt);
    }
}
