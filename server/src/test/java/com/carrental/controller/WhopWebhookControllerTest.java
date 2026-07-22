package com.carrental.controller;

import com.carrental.entity.PromoCode;
import com.carrental.entity.PromoCodeRedemption;
import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.Tenant;
import com.carrental.repository.*;
import com.carrental.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers webhook-driven promo redemption reconciliation: a RESERVED
 * redemption (created when the checkout link was issued) must only ever
 * flip to USED once a real, verified Whop event confirms payment — never
 * at link-creation time, and never twice for the same event.
 */
@ExtendWith(MockitoExtension.class)
class WhopWebhookControllerTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionInvoiceRepository invoiceRepository;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    @Mock private SubscriptionEventRepository subscriptionEventRepository;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks
    private WhopWebhookController controller;

    @Test
    void activation_confirmsReservedRedemption_forMatchingPromoAndTenant() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        Tenant tenant = Tenant.builder().id(42L).planName("BASIC").build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(1L).code("BASIC").whopPlanId("whop_basic_monthly").build();
        PromoCodeRedemption reservation = PromoCodeRedemption.builder()
                .id(7L).status("RESERVED")
                .promoCode(PromoCode.builder().id(9L).code("WELCOME20").build())
                .tenant(tenant)
                .originalPrice(new BigDecimal("199.00"))
                .discountAmount(new BigDecimal("39.80"))
                .finalPrice(new BigDecimal("159.20"))
                .build();

        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant));
        when(subscriptionEventRepository.existsByWhopEventId(anyString())).thenReturn(false);
        when(planRepository.findAll()).thenReturn(java.util.List.of(plan));
        when(subscriptionService.activatePaidPlan(any(), any(), anyInt())).thenReturn(tenant);
        when(promoCodeRedemptionRepository.findFirstByPromoCode_CodeIgnoreCaseAndTenantIdAndStatusOrderByRedeemedAtDesc(
                "WELCOME20", 42L, "RESERVED")).thenReturn(Optional.of(reservation));

        String body = "{\"event\":\"membership.went_valid\",\"id\":\"mem_123\",\"plan_id\":\"whop_basic_monthly\","
                + "\"metadata\":{\"tenant_id\":\"42\",\"promo_code\":\"WELCOME20\"}}";

        controller.handleWhopWebhook(body, null, null);

        ArgumentCaptor<PromoCodeRedemption> captor = ArgumentCaptor.forClass(PromoCodeRedemption.class);
        verify(promoCodeRedemptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("USED");
        assertThat(captor.getValue().getId()).isEqualTo(7L);
    }

    @Test
    void activation_noPromoInMetadata_neverTouchesRedemptions() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        Tenant tenant = Tenant.builder().id(42L).planName("BASIC").build();
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant));
        when(subscriptionEventRepository.existsByWhopEventId(anyString())).thenReturn(false);

        String body = "{\"event\":\"membership.went_valid\",\"id\":\"mem_456\",\"metadata\":{\"tenant_id\":\"42\"}}";

        controller.handleWhopWebhook(body, null, null);

        verifyNoInteractions(promoCodeRedemptionRepository);
    }

    @Test
    void duplicateEvent_isSkipped_neverActivatesTwice() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        when(subscriptionEventRepository.existsByWhopEventId("membership.went_valid:mem_dup")).thenReturn(true);

        String body = "{\"event\":\"membership.went_valid\",\"id\":\"mem_dup\",\"metadata\":{\"tenant_id\":\"42\"}}";

        controller.handleWhopWebhook(body, null, null);

        verifyNoInteractions(subscriptionService);
        verify(subscriptionEventRepository, never()).save(any());
    }
}
