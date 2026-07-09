package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "trusted_devices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "fingerprint_hash"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "fingerprint_hash", nullable = false, length = 128)
    private String fingerprintHash;

    @Column(name = "device_name", length = 150)
    private String deviceName;

    @Column(length = 80)
    private String browser;

    @Column(name = "operating_system", length = 80)
    private String operatingSystem;

    @Column(name = "last_ip_address", length = 64)
    private String lastIpAddress;

    @Column(nullable = false)
    private Boolean trusted;

    @Column(nullable = false)
    private Boolean blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "trusted_at")
    private LocalDateTime trustedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
        if (trusted == null) trusted = false;
        if (blocked == null) blocked = false;
    }

    /** True when this device has been explicitly trusted and the trust has not expired or been revoked. */
    public boolean isActiveTrust() {
        return Boolean.TRUE.equals(trusted)
                && revokedAt == null
                && expiresAt != null
                && LocalDateTime.now().isBefore(expiresAt);
    }
}
