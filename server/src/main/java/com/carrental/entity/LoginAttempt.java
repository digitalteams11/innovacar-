package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks failed login attempts for rate limiting and account lockout.
 */
@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Column(name = "successful")
    private Boolean successful;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "device_name", length = 150)
    private String deviceName;

    @Column(name = "browser", length = 80)
    private String browser;

    @Column(name = "operating_system", length = 80)
    private String operatingSystem;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "suspicious")
    private Boolean suspicious;

    @PrePersist
    protected void onCreate() {
        attemptedAt = LocalDateTime.now();
        if (successful == null) successful = false;
        if (suspicious == null) suspicious = false;
    }
}
