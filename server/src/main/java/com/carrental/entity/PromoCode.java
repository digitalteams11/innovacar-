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
    private String promotionType; // DISCOUNT, FREE_MONTHS, FREE_DAYS, FREE_FEATURE, PLAN_TRIAL

    @Column(name = "free_months")
    private Integer freeMonths;

    @Column(name = "free_days")
    private Integer freeDays;

    @Column(name = "free_feature_code")
    private String freeFeatureCode;

    @Column(name = "trial_plan_code")
    private String trialPlanCode;

    @Column(length = 500)
    private String description;

    /** MONTHLY, YEARLY, or BOTH (null is treated as BOTH). */
    @Column(name = "billing_cycle", length = 20)
    private String billingCycle;

    /** Max redemptions allowed for a single agency/tenant (null = unlimited). */
    @Column(name = "max_uses_per_agency")
    private Integer maxUsesPerAgency;

    /** Minimum order subtotal required before this code can be applied. */
    @Column(name = "minimum_amount", precision = 10, scale = 2)
    private BigDecimal minimumAmount;

    @Column(length = 10)
    private String currency;

    @Column(name = "created_by_super_admin_id")
    private Long createdBySuperAdminId;

    @Column
    private Boolean isActive;

    /** Only valid for agencies that have never had a paid subscription. */
    @Column(name = "first_time_only")
    private Boolean firstTimeOnly;

    /** If true, the plan restriction (applicablePlans) is ignored. */
    @Column(name = "applies_to_all_plans")
    private Boolean appliesToAllPlans;

    /** Soft-delete flag — deleted promos are hidden from all listings. */
    @Column(name = "deleted")
    private Boolean deleted;

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
        if (firstTimeOnly == null) firstTimeOnly = false;
        if (appliesToAllPlans == null) appliesToAllPlans = false;
        if (deleted == null) deleted = false;
        if (currency == null || currency.isBlank()) currency = "MAD";
        if (billingCycle == null || billingCycle.isBlank()) billingCycle = "BOTH";
        if (code != null) code = code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (code != null) code = code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
