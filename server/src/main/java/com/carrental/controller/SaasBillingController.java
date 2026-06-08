package com.carrental.controller;

import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@RestController
@RequestMapping("/api/saas")
@RequiredArgsConstructor
public class SaasBillingController {

    private final SubscriptionPlanRepository planRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PaymentGatewayConfigRepository gatewayRepository;
    private final TenantRepository tenantRepository;
    private final AffiliateReferralRepository affiliateReferralRepository;
    private final AffiliateRuleRepository affiliateRuleRepository;
    private final AffiliateConversionRepository affiliateConversionRepository;
    private final TenantFeatureOverrideRepository featureOverrideRepository;
    private final SubscriptionInvoiceRepository subscriptionInvoiceRepository;
    private final PaymentRepository paymentRepository;
    private final com.carrental.service.EmailService emailService;

    @PostMapping("/checkout/prepare")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> prepareCheckout(@RequestBody Map<String, String> body) {
        String planCode = body.get("planCode");
        String billingCycle = body.getOrDefault("billingCycle", "monthly");
        String coupon = body.get("couponCode");

        SubscriptionPlan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        BigDecimal subtotal = "yearly".equalsIgnoreCase(billingCycle) ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        PromoCode promotion = findValidPromotion(coupon, plan.getCode());
        BigDecimal discount = calculateDiscount(promotion, subtotal);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planCode", plan.getCode());
        result.put("planName", plan.getName());
        result.put("billingCycle", billingCycle);
        result.put("subtotal", subtotal);
        result.put("discount", discount);
        result.put("total", total);
        result.put("currency", "MAD");
        result.put("promotion", promotionDetails(promotion));
        result.put("paymentMethods", gatewayRepository.findAllByEnabledTrue().stream().map(PaymentGatewayConfig::getProvider).toList());
        result.put("flow", List.of("Select Plan", "Select Billing Cycle", "Apply Coupon", "Choose Payment Method", "Pay", "Activate Plan", "Generate Invoice", "Send Email"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/checkout/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> completeCheckout(@RequestBody Map<String, String> body) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        String planCode = body.get("planCode");
        String billingCycle = body.getOrDefault("billingCycle", "monthly");
        String couponCode = body.get("couponCode");
        String provider = body.get("paymentMethod");
        String gatewayReference = body.get("gatewayReference");
        String referralCode = body.get("referralCode");

        SubscriptionPlan selectedPlan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        PaymentGatewayConfig gateway = gatewayRepository.findByProvider(provider)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .orElseThrow(() -> new IllegalArgumentException("Payment gateway is not enabled"));

        PromoCode promotion = findValidPromotion(couponCode, selectedPlan.getCode());
        SubscriptionPlan activatedPlan = resolveActivatedPlan(selectedPlan, promotion);
        BigDecimal subtotal = "yearly".equalsIgnoreCase(billingCycle)
                ? selectedPlan.getYearlyPrice() : selectedPlan.getMonthlyPrice();
        BigDecimal discount = calculateDiscount(promotion, subtotal);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);
        LocalDateTime paidAt = LocalDateTime.now();

        SubscriptionInvoice invoice = subscriptionInvoiceRepository.save(SubscriptionInvoice.builder()
                .invoiceNumber("SAAS-" + Year.now().getValue() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .tenant(tenant)
                .plan(activatedPlan)
                .billingCycle(billingCycle)
                .subtotal(subtotal)
                .discount(discount)
                .total(total)
                .currency("MAD")
                .status("PAID")
                .gatewayProvider(gateway.getProvider())
                .gatewayReference(gatewayReference)
                .couponCode(couponCode)
                .paidAt(paidAt)
                .build());

        paymentRepository.save(Payment.builder()
                .paymentNumber("SUB-" + Year.now().getValue() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .amount(total)
                .paymentDate(paidAt)
                .paymentMethod(PaymentMethod.ONLINE)
                .reference(gatewayReference)
                .status(PaymentStatus.PAID)
                .type(PaymentType.SUBSCRIPTION)
                .tenant(tenant)
                .notes("Subscription invoice " + invoice.getInvoiceNumber())
                .build());

        tenant.setPlanName(activatedPlan.getName());
        tenant.setSubscriptionActive(true);
        tenant.setStatus("ACTIVE");
        LocalDate baseDate = tenant.getSubscriptionEndDate() != null && tenant.getSubscriptionEndDate().isAfter(LocalDate.now())
                ? tenant.getSubscriptionEndDate() : LocalDate.now();
        int subscriptionMonths = "yearly".equalsIgnoreCase(billingCycle) ? 12 : 1;
        if (promotion != null && "FREE_MONTHS".equalsIgnoreCase(promotion.getPromotionType())) {
            subscriptionMonths += Optional.ofNullable(promotion.getFreeMonths()).orElse(1);
        }
        if (promotion != null && "PLAN_TRIAL".equalsIgnoreCase(promotion.getPromotionType())) {
            subscriptionMonths = Optional.ofNullable(promotion.getFreeMonths()).orElse(1);
        }
        tenant.setSubscriptionEndDate(baseDate.plusMonths(subscriptionMonths));
        tenant.setMaxVehicles(activatedPlan.getMaxVehicles());
        tenant.setMaxEmployees(activatedPlan.getMaxEmployees());
        tenant.setMaxGpsDevices(activatedPlan.getMaxGpsDevices());
        tenant.setMaxReservations(activatedPlan.getMaxReservations());
        tenant.setStorageLimitMb(activatedPlan.getStorageLimitMb());
        tenantRepository.save(tenant);

        if (promotion != null) {
            promotion.setUsedCount(Optional.ofNullable(promotion.getUsedCount()).orElse(0) + 1);
            promoCodeRepository.save(promotion);
            applyFeaturePromotion(tenant, promotion, paidAt);
        }
        applyAffiliateReward(referralCode, tenant, total, paidAt);

        emailService.sendCustomerSuccessEmail(tenant.getEmail(), "Subscription activated",
                "Your " + activatedPlan.getName() + " subscription is active. Invoice: " + invoice.getInvoiceNumber());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("planName", activatedPlan.getName());
        response.put("invoiceNumber", invoice.getInvoiceNumber());
        response.put("paymentStatus", "PAID");
        response.put("subscriptionEndDate", tenant.getSubscriptionEndDate());
        response.put("promotion", promotionDetails(promotion));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getSubscriptionInvoices() {
        return ResponseEntity.ok(subscriptionInvoiceRepository.findAllByTenantIdOrderByIssuedAtDesc(
                TenantContext.getCurrentTenantId()).stream().map(invoice -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", invoice.getId());
                    row.put("invoiceNumber", invoice.getInvoiceNumber());
                    row.put("planName", invoice.getPlan().getName());
                    row.put("billingCycle", invoice.getBillingCycle());
                    row.put("subtotal", invoice.getSubtotal());
                    row.put("discount", invoice.getDiscount());
                    row.put("total", invoice.getTotal());
                    row.put("currency", invoice.getCurrency());
                    row.put("status", invoice.getStatus());
                    row.put("gatewayProvider", invoice.getGatewayProvider());
                    row.put("gatewayReference", invoice.getGatewayReference());
                    row.put("couponCode", invoice.getCouponCode());
                    row.put("issuedAt", invoice.getIssuedAt());
                    row.put("paidAt", invoice.getPaidAt());
                    return row;
                }).toList());
    }

    @PostMapping("/affiliate/code")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getOrCreateReferralCode() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        AffiliateReferral referral = affiliateReferralRepository.findByReferrerTenantId(tenantId)
                .orElseGet(() -> affiliateReferralRepository.save(AffiliateReferral.builder()
                        .referrerTenant(tenant)
                        .referralCode("AGENCY-" + tenantId)
                        .status("ACTIVE")
                        .build()));
        return ResponseEntity.ok(Map.of("referralCode", referral.getReferralCode(), "status", referral.getStatus()));
    }

    @PostMapping("/affiliate/apply")
    public ResponseEntity<Map<String, Object>> applyReferral(@RequestBody Map<String, String> body) {
        String referralCode = body.get("referralCode");
        AffiliateReferral referral = affiliateReferralRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        AffiliateRule rule = affiliateRuleRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "referrerTenantId", referral.getReferrerTenant().getId(),
                "rewardType", rule != null ? rule.getRewardType() : "FREE_MONTH",
                "freeMonths", rule != null ? Optional.ofNullable(rule.getFreeMonths()).orElse(1) : 1,
                "commissionPercent", rule != null ? Optional.ofNullable(rule.getCommissionPercent()).orElse(BigDecimal.ZERO) : BigDecimal.ZERO
        ));
    }

    private PromoCode findValidPromotion(String coupon, String planCode) {
        if (coupon == null || coupon.isBlank()) return null;
        PromoCode promo = promoCodeRepository.findByCode(coupon.trim().toUpperCase(Locale.ROOT)).orElse(null);
        if (promo == null || !Boolean.TRUE.equals(promo.getIsActive())) return null;
        LocalDate today = LocalDate.now();
        if (promo.getValidFrom() != null && today.isBefore(promo.getValidFrom())) return null;
        if (promo.getValidTo() != null && today.isAfter(promo.getValidTo())) return null;
        if (promo.getMaxUses() != null && Optional.ofNullable(promo.getUsedCount()).orElse(0) >= promo.getMaxUses()) {
            return null;
        }
        if (promo.getApplicablePlans() != null && !promo.getApplicablePlans().isBlank()) {
            boolean applies = Arrays.stream(promo.getApplicablePlans().split(","))
                    .map(String::trim)
                    .anyMatch(value -> value.equalsIgnoreCase(planCode));
            if (!applies) return null;
        }
        return promo;
    }

    private BigDecimal calculateDiscount(PromoCode promo, BigDecimal subtotal) {
        if (promo == null) {
            return BigDecimal.ZERO;
        }
        if ("PLAN_TRIAL".equalsIgnoreCase(promo.getPromotionType())) return subtotal;
        if (promo.getPromotionType() != null && !"DISCOUNT".equalsIgnoreCase(promo.getPromotionType())) {
            return BigDecimal.ZERO;
        }
        if ("PERCENTAGE".equalsIgnoreCase(promo.getDiscountType())) {
            return subtotal.multiply(promo.getDiscountValue()).divide(new BigDecimal("100"));
        }
        return promo.getDiscountValue() != null ? promo.getDiscountValue().min(subtotal) : BigDecimal.ZERO;
    }

    private SubscriptionPlan resolveActivatedPlan(SubscriptionPlan selectedPlan, PromoCode promotion) {
        if (promotion == null || !"PLAN_TRIAL".equalsIgnoreCase(promotion.getPromotionType())
                || promotion.getTrialPlanCode() == null || promotion.getTrialPlanCode().isBlank()) {
            return selectedPlan;
        }
        return planRepository.findByCode(promotion.getTrialPlanCode())
                .orElseThrow(() -> new IllegalArgumentException("Promotion trial plan not found"));
    }

    private void applyFeaturePromotion(Tenant tenant, PromoCode promotion, LocalDateTime startsAt) {
        if (!"FREE_FEATURE".equalsIgnoreCase(promotion.getPromotionType())
                || promotion.getFreeFeatureCode() == null || promotion.getFreeFeatureCode().isBlank()) {
            return;
        }
        int months = Optional.ofNullable(promotion.getFreeMonths()).orElse(1);
        featureOverrideRepository.save(TenantFeatureOverride.builder()
                .tenant(tenant)
                .featureCode(promotion.getFreeFeatureCode().trim().toUpperCase(Locale.ROOT))
                .enabled(true)
                .startsAt(startsAt)
                .expiresAt(startsAt.plusMonths(months))
                .source("PROMOTION:" + promotion.getCode())
                .build());
    }

    private void applyAffiliateReward(String referralCode, Tenant referredTenant, BigDecimal total, LocalDateTime convertedAt) {
        if (referralCode == null || referralCode.isBlank()
                || affiliateConversionRepository.findByReferredTenantId(referredTenant.getId()).isPresent()) {
            return;
        }
        AffiliateReferral referral = affiliateReferralRepository.findByReferralCode(referralCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        if (referral.getReferrerTenant().getId().equals(referredTenant.getId())) {
            throw new IllegalArgumentException("An agency cannot use its own referral code");
        }
        AffiliateRule rule = affiliateRuleRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        if (rule == null) return;

        Integer freeMonths = null;
        BigDecimal commission = null;
        if ("FREE_MONTH".equalsIgnoreCase(rule.getRewardType())) {
            freeMonths = Optional.ofNullable(rule.getFreeMonths()).orElse(1);
            Tenant referrer = referral.getReferrerTenant();
            LocalDate rewardBase = referrer.getSubscriptionEndDate() != null
                    && referrer.getSubscriptionEndDate().isAfter(LocalDate.now())
                    ? referrer.getSubscriptionEndDate() : LocalDate.now();
            referrer.setSubscriptionEndDate(rewardBase.plusMonths(freeMonths));
            tenantRepository.save(referrer);
        } else if ("COMMISSION".equalsIgnoreCase(rule.getRewardType())) {
            BigDecimal percent = Optional.ofNullable(rule.getCommissionPercent()).orElse(BigDecimal.ZERO);
            commission = total.multiply(percent).divide(new BigDecimal("100"));
        }

        affiliateConversionRepository.save(AffiliateConversion.builder()
                .referral(referral)
                .referredTenant(referredTenant)
                .rewardType(rule.getRewardType())
                .freeMonthsAwarded(freeMonths)
                .commissionAmount(commission)
                .convertedAt(convertedAt)
                .build());
    }

    private Map<String, Object> promotionDetails(PromoCode promotion) {
        if (promotion == null) return Map.of();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", promotion.getCode());
        details.put("name", Optional.ofNullable(promotion.getPromotionName()).orElse(promotion.getCode()));
        details.put("type", Optional.ofNullable(promotion.getPromotionType()).orElse("DISCOUNT"));
        details.put("freeMonths", Optional.ofNullable(promotion.getFreeMonths()).orElse(0));
        details.put("freeFeatureCode", Optional.ofNullable(promotion.getFreeFeatureCode()).orElse(""));
        details.put("trialPlanCode", Optional.ofNullable(promotion.getTrialPlanCode()).orElse(""));
        return details;
    }
}
