package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String discountType; // PERCENTAGE or FIXED

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column
    private Integer maxUses;

    @Column
    private Integer usedCount;

    @Column
    private LocalDate validFrom;

    @Column
    private LocalDate validTo;

    @Column
    private String applicablePlans; // comma-separated plan codes

    @Column(name = "promotion_name")
    private String promotionName;

    @Column(name = "promotion_type")
    private String promotionType; // DISCOUNT, FREE_MONTHS, FREE_FEATURE, PLAN_TRIAL

    @Column(name = "free_months")
    private Integer freeMonths;

    @Column(name = "free_feature_code")
    private String freeFeatureCode;

    @Column(name = "trial_plan_code")
    private String trialPlanCode;

    @Column
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (usedCount == null) usedCount = 0;
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
