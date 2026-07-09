package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One record per agency that successfully used a promo code at checkout.
 * Created/confirmed at the moment the invoice is paid so a checkout that's
 * opened but never paid never consumes a redemption slot.
 */
@Entity
@Table(name = "promo_code_redemptions", indexes = {
        @Index(name = "idx_promo_redemption_promo", columnList = "promo_code_id"),
        @Index(name = "idx_promo_redemption_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCodeRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCode promoCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "subscription_invoice_id")
    private Long subscriptionInvoiceId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_price", precision = 10, scale = 2)
    private BigDecimal finalPrice;

    /** RESERVED → created when checkout session starts; USED → after payment confirmed; EXPIRED/CANCELLED otherwise. */
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "whop_checkout_url", length = 500)
    private String whopCheckoutUrl;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @PrePersist
    protected void onCreate() {
        if (redeemedAt == null) redeemedAt = LocalDateTime.now();
        if (status == null) status = "RESERVED";
    }
}
