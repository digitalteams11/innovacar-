package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "affiliate_conversions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_affiliate_referred_tenant", columnNames = "referred_tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffiliateConversion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referral_id", nullable = false)
    private AffiliateReferral referral;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referred_tenant_id", nullable = false)
    private Tenant referredTenant;

    @Column(name = "reward_type", nullable = false, length = 30)
    private String rewardType;

    @Column(name = "free_months_awarded")
    private Integer freeMonthsAwarded;

    @Column(name = "commission_amount", precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "converted_at", nullable = false)
    private LocalDateTime convertedAt;

    @PrePersist
    protected void onCreate() {
        if (convertedAt == null) convertedAt = LocalDateTime.now();
    }
}
