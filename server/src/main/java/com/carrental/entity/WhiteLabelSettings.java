package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "white_label_settings", indexes = {
        @Index(name = "idx_white_label_tenant", columnList = "tenant_id", unique = true),
        @Index(name = "idx_white_label_domain", columnList = "custom_domain", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhiteLabelSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "accent_color", length = 20)
    private String accentColor;

    @Column(name = "custom_domain", unique = true, length = 255)
    private String customDomain;

    @Column(name = "subdomain", unique = true, length = 63)
    private String subdomain;

    @Column(name = "domain_status", length = 30)
    private String domainStatus; // NONE, PENDING, DNS_VERIFIED, FAILED, ACTIVE

    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Column(name = "dns_verified_at")
    private LocalDateTime dnsVerifiedAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_check_error", columnDefinition = "TEXT")
    private String lastCheckError;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (domainStatus == null) domainStatus = "NONE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
