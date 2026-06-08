package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "affiliate_referrals", indexes = {
        @Index(name = "idx_affiliate_code", columnList = "referral_code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffiliateReferral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_tenant_id", nullable = false)
    private Tenant referrerTenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_tenant_id")
    private Tenant referredTenant;

    @Column(name = "referral_code", nullable = false, unique = true, length = 80)
    private String referralCode;

    @Column(length = 30)
    private String status; // ACTIVE, CONVERTED, PAID

    @Column(length = 30)
    private String rewardType;

    @Column
    private Integer freeMonthsAwarded;

    @Column(precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }
}
