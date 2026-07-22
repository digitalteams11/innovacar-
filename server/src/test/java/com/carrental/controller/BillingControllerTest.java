package com.carrental.controller;

import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.FeatureAccessService;
import com.carrental.service.PlanLimitService;
import com.carrental.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the production incident: a promo code with a real discount
 * (WELCOME20-style) must never silently fall back to the full-price Whop
 * checkout URL just because no discounted override link was configured —
 * that fallback is exactly how a customer got quoted 159.20 MAD in Innovacar
 * and billed 199 MAD by Whop.
 */
@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    private static final long TENANT_ID = 42L;

    @Mock private SubscriptionPlanRepository    planRepository;
    @Mock private PromoCodeRepository           promoCodeRepository;
    @Mock private PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    @Mock private PromoCodePlanLinkRepository   promoCodePlanLinkRepository;
    @Mock private TenantRepository              tenantRepository;
    @Mock private SubscriptionInvoiceRepository invoiceRepository;
    @Mock private SubscriptionService           subscriptionService;
    @Mock private FeatureAccessService          featureAccessService;
    @Mock private PlanLimitService              planLimitService;

    @InjectMocks
    private BillingController billingController;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        ReflectionTestUtils.setField(billingController, "whopApiKey", "");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SubscriptionPlan basicPlan() {
        return SubscriptionPlan.builder()
                .id(1L).code("BASIC").name("Basic")
                .monthlyPrice(new BigDecimal("199.00"))
                .yearlyPrice(new BigDecimal("1990.00"))
                .currency("MAD")
                .isActive(true)
                .billingCycleAllowedMonthly(true)
                .whopCheckoutUrlMonthly("https://whop.com/checkout/basic-monthly")
                .whopCheckoutUrlYearly("https://whop.com/checkout/basic-yearly")
                .build();
    }

    private PromoCode welcome20() {
        return PromoCode.builder()
                .id(9L).code("WELCOME20")
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("20"))
                .promotionType("DISCOUNT")
                .isActive(true)
                .deleted(false)
                .appliesToAllPlans(true)
                .build();
    }

    private Tenant tenant() {
        return Tenant.builder().id(TENANT_ID).name("Test Agency").build();
    }

    @Test
    void checkout_realDiscountWithoutOverrideLink_isBlockedNotSilentlyFullPrice() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(planRepository.findByCode("BASIC")).thenReturn(Optional.of(basicPlan()));
        when(promoCodeRepository.findByCodeAndDeletedFalse("WELCOME20")).thenReturn(Optional.of(welcome20()));
        when(promoCodePlanLinkRepository.findByPromoCodeIdAndPlanCodeIgnoreCaseAndBillingCycleIgnoreCaseAndActiveTrue(
                9L, "BASIC", "MONTHLY")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = billingController.createCheckout(Map.of(
                "planCode", "BASIC", "billingCycle", "MONTHLY", "promoCode", "WELCOME20"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("errorCode", "PROMO_CHECKOUT_NOT_CONFIGURED");
        // The critical assertion: no checkout URL of any kind is ever returned.
        assertThat(response.getBody()).doesNotContainKey("checkoutUrl");
        // And no reservation/redemption is recorded for a checkout that never happened.
        verify(promoCodeRedemptionRepository, never()).save(any());
    }

    @Test
    void checkout_realDiscountWithOverrideLink_usesDiscountedWhopUrl() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(planRepository.findByCode("BASIC")).thenReturn(Optional.of(basicPlan()));
        when(promoCodeRepository.findByCodeAndDeletedFalse("WELCOME20")).thenReturn(Optional.of(welcome20()));
        PromoCodePlanLink link = PromoCodePlanLink.builder()
                .whopCheckoutUrlOverride("https://whop.com/checkout/basic-monthly-welcome20")
                .active(true).build();
        when(promoCodePlanLinkRepository.findByPromoCodeIdAndPlanCodeIgnoreCaseAndBillingCycleIgnoreCaseAndActiveTrue(
                9L, "BASIC", "MONTHLY")).thenReturn(Optional.of(link));

        ResponseEntity<Map<String, Object>> response = billingController.createCheckout(Map.of(
                "planCode", "BASIC", "billingCycle", "MONTHLY", "promoCode", "WELCOME20"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("success", true);
        String checkoutUrl = (String) response.getBody().get("checkoutUrl");
        assertThat(checkoutUrl).startsWith("https://whop.com/checkout/basic-monthly-welcome20");
        assertThat(response.getBody().get("finalPrice")).isEqualTo(new BigDecimal("159.20"));
        verify(promoCodeRedemptionRepository).save(any());
    }

    @Test
    void checkout_noPromo_usesNormalPlanUrl() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(planRepository.findByCode("BASIC")).thenReturn(Optional.of(basicPlan()));

        ResponseEntity<Map<String, Object>> response = billingController.createCheckout(Map.of(
                "planCode", "BASIC", "billingCycle", "MONTHLY"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String checkoutUrl = (String) response.getBody().get("checkoutUrl");
        assertThat(checkoutUrl).startsWith("https://whop.com/checkout/basic-monthly");
        verify(promoCodeRedemptionRepository, never()).save(any());
    }

    @Test
    void checkout_zeroDiscountPromo_doesNotRequireOverrideLink() {
        // e.g. a FREE_MONTHS-style promo: price is unchanged, so Whop's normal
        // price already matches the quote — no override link should be required.
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(planRepository.findByCode("BASIC")).thenReturn(Optional.of(basicPlan()));
        PromoCode freeMonthsPromo = PromoCode.builder()
                .id(11L).code("BONUSMONTH")
                .promotionType("FREE_MONTHS")
                .freeMonths(1)
                .isActive(true).deleted(false).appliesToAllPlans(true)
                .build();
        when(promoCodeRepository.findByCodeAndDeletedFalse("BONUSMONTH")).thenReturn(Optional.of(freeMonthsPromo));
        when(promoCodePlanLinkRepository.findByPromoCodeIdAndPlanCodeIgnoreCaseAndBillingCycleIgnoreCaseAndActiveTrue(
                11L, "BASIC", "MONTHLY")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = billingController.createCheckout(Map.of(
                "planCode", "BASIC", "billingCycle", "MONTHLY", "promoCode", "BONUSMONTH"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("checkoutUrl")).isNotNull();
    }

    @Test
    void validatePromo_welcome20OnBasicMonthly_quotes159_20() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        when(planRepository.findById(1L)).thenReturn(Optional.of(basicPlan()));
        when(promoCodeRepository.findByCodeAndDeletedFalse("WELCOME20")).thenReturn(Optional.of(welcome20()));
        when(promoCodePlanLinkRepository.findByPromoCodeIdAndPlanCodeIgnoreCaseAndBillingCycleIgnoreCaseAndActiveTrue(
                9L, "BASIC", "MONTHLY")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = billingController.validatePromo(Map.of(
                "code", "welcome20", "planId", "1", "billingCycle", "MONTHLY"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("originalAmount")).isEqualTo(new BigDecimal("199.00"));
        assertThat(data.get("discountAmount")).isEqualTo(new BigDecimal("39.80"));
        assertThat(data.get("finalAmount")).isEqualTo(new BigDecimal("159.20"));
        // No override link exists yet — the frontend must be told so it can warn
        // the user before they click "Continue to checkout", not just fail there.
        assertThat(data.get("checkoutConfigured")).isEqualTo(false);
    }

    @Test
    void validatePromo_expiredCode_isRejected() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
        PromoCode expired = welcome20();
        expired.setValidTo(LocalDate.now().minusDays(1));
        when(promoCodeRepository.findByCodeAndDeletedFalse("WELCOME20")).thenReturn(Optional.of(expired));

        ResponseEntity<Map<String, Object>> response = billingController.validatePromo(Map.of("code", "WELCOME20"));

        assertThat(response.getBody()).containsEntry("success", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("errorCode")).isEqualTo("PROMO_CODE_EXPIRED");
    }
}
