package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps a promo code + plan + billing cycle to a dedicated Whop checkout URL
 * that already prices the discounted amount (Mode 1 static Whop links).
 *
 * If no row exists for a given promo+plan+cycle combination, the checkout
 * returns PROMO_CHECKOUT_NOT_CONFIGURED — we never fake a discount.
 */
@Entity
@Table(name = "promo_code_plan_links", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"promo_code_id", "plan_code", "billing_cycle"},
                          name = "uk_pcpl_promo_plan_cycle")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCodePlanLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCode promoCode;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle;

    /** Whop checkout URL that charges the discounted price. Null means not configured. */
    @Column(name = "whop_checkout_url_override", length = 500)
    private String whopCheckoutUrlOverride;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (active == null) active = true;
    }
}
