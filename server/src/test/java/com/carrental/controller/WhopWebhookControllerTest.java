package com.carrental.controller;

import com.carrental.entity.PromoCode;
import com.carrental.entity.PromoCodeRedemption;
import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.SubscriptionStatus;
import com.carrental.entity.Tenant;
import com.carrental.repository.*;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PlatformEmailService platformEmailService;

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

    // Behavior 6: activation while previously GRACE_PERIOD calls the shared reactivate() path,
    // not just activatePaidPlan.
    @Test
    void activation_fromGracePeriod_callsReactivate() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        Tenant tenant = Tenant.builder().id(50L).planName("Innovacar Complete").status(SubscriptionStatus.GRACE_PERIOD).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(1L).code("COMPLETE").whopPlanId("whop_complete_monthly").build();

        when(tenantRepository.findById(50L)).thenReturn(Optional.of(tenant));
        when(subscriptionEventRepository.existsByWhopEventId(anyString())).thenReturn(false);
        when(planRepository.findAll()).thenReturn(java.util.List.of(plan));
        when(subscriptionService.activatePaidPlan(any(), any(), anyInt())).thenReturn(tenant);
        when(subscriptionService.reactivate(any(), any(), any())).thenReturn(tenant);

        String body = "{\"event\":\"membership.went_valid\",\"id\":\"mem_grace\",\"plan_id\":\"whop_complete_monthly\","
                + "\"metadata\":{\"tenant_id\":\"50\"}}";

        controller.handleWhopWebhook(body, null, null);

        verify(subscriptionService).activatePaidPlan(eq(tenant), eq(plan), eq(1));
        verify(subscriptionService).reactivate(eq(tenant), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(platformEmailService, never()).sendSubscriptionActivated(any());
    }

    // Behavior 6: activation while previously SUSPENDED also calls reactivate().
    @Test
    void activation_fromSuspended_callsReactivate() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        Tenant tenant = Tenant.builder().id(51L).planName("Innovacar Complete").status(SubscriptionStatus.SUSPENDED).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(1L).code("COMPLETE").whopPlanId("whop_complete_monthly").build();

        when(tenantRepository.findById(51L)).thenReturn(Optional.of(tenant));
        when(subscriptionEventRepository.existsByWhopEventId(anyString())).thenReturn(false);
        when(planRepository.findAll()).thenReturn(java.util.List.of(plan));
        when(subscriptionService.activatePaidPlan(any(), any(), anyInt())).thenReturn(tenant);
        when(subscriptionService.reactivate(any(), any(), any())).thenReturn(tenant);

        String body = "{\"event\":\"payment.succeeded\",\"id\":\"mem_susp\",\"plan_id\":\"whop_complete_monthly\","
                + "\"metadata\":{\"tenant_id\":\"51\"}}";

        controller.handleWhopWebhook(body, null, null);

        verify(subscriptionService).reactivate(eq(tenant), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    // Behavior 6: activation from a fresh/trial/cancelled state does NOT call reactivate(), and
    // sends the fresh-activation email instead.
    @Test
    void activation_fromTrial_doesNotCallReactivate_sendsFreshActivationEmail() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        Tenant tenant = Tenant.builder().id(52L).planName("Trial").status(SubscriptionStatus.TRIAL).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(1L).code("COMPLETE").whopPlanId("whop_complete_monthly").build();

        when(tenantRepository.findById(52L)).thenReturn(Optional.of(tenant));
        when(subscriptionEventRepository.existsByWhopEventId(anyString())).thenReturn(false);
        when(planRepository.findAll()).thenReturn(java.util.List.of(plan));
        when(subscriptionService.activatePaidPlan(any(), any(), anyInt())).thenReturn(tenant);

        String body = "{\"event\":\"membership.went_valid\",\"id\":\"mem_fresh\",\"plan_id\":\"whop_complete_monthly\","
                + "\"metadata\":{\"tenant_id\":\"52\"}}";

        controller.handleWhopWebhook(body, null, null);

        verify(subscriptionService, never()).reactivate(any(), any(), any());
        verify(platformEmailService).sendSubscriptionActivated(tenant);
    }

    @Test
    void activation_fromCancelled_doesNotCallReactivate() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        Tenant tenant = Tenant.builder().id(53L).planName("Innovacar Complete").status(SubscriptionStatus.CANCELLED).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(1L).code("COMPLETE").whopPlanId("whop_complete_monthly").build();

        when(tenantRepository.findById(53L)).thenReturn(Optional.of(tenant));
        when(subscriptionEventRepository.existsByWhopEventId(anyString())).thenReturn(false);
        when(planRepository.findAll()).thenReturn(java.util.List.of(plan));
        when(subscriptionService.activatePaidPlan(any(), any(), anyInt())).thenReturn(tenant);

        String body = "{\"event\":\"membership.went_valid\",\"id\":\"mem_cancelled\",\"plan_id\":\"whop_complete_monthly\","
                + "\"metadata\":{\"tenant_id\":\"53\"}}";

        controller.handleWhopWebhook(body, null, null);

        verify(subscriptionService, never()).reactivate(any(), any(), any());
    }

    // Behavior 7: a payment-failure webhook goes through startGracePeriod (not a direct
    // PAST_DUE-style set) and the resulting deadline is in the future, never an immediate suspend.
    @Test
    void paymentFailed_callsStartGracePeriod_withFutureDeadline_neverImmediateSuspension() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        Tenant tenant = Tenant.builder().id(60L).planName("Innovacar Complete").status(SubscriptionStatus.ACTIVE).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(1L).code("COMPLETE").gracePeriodDays(3).build();
        Tenant afterGrace = Tenant.builder().id(60L).planName("Innovacar Complete")
                .status(SubscriptionStatus.GRACE_PERIOD)
                .gracePeriodEnd(LocalDateTime.now().plusDays(3))
                .build();

        when(tenantRepository.findById(60L)).thenReturn(Optional.of(tenant));
        when(subscriptionEventRepository.existsByWhopEventId(anyString())).thenReturn(false);
        when(planRepository.findByName(any())).thenReturn(Optional.of(plan));
        when(subscriptionService.startGracePeriod(tenant, plan)).thenReturn(afterGrace);

        String body = "{\"event\":\"payment.failed\",\"id\":\"mem_fail\",\"metadata\":{\"tenant_id\":\"60\"}}";

        controller.handleWhopWebhook(body, null, null);

        verify(subscriptionService).startGracePeriod(tenant, plan);
        assertThat(afterGrace.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(afterGrace.getGracePeriodEnd()).isAfter(LocalDateTime.now());
    }

    // Behavior 8 (regression): duplicate webhook events remain a no-op even for the new
    // payment-failure/activation paths, not just the promo-redemption path already covered above.
    @Test
    void duplicateEvent_isSkipped_forPaymentFailedEventToo() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        when(subscriptionEventRepository.existsByWhopEventId("payment.failed:mem_dup2")).thenReturn(true);

        String body = "{\"event\":\"payment.failed\",\"id\":\"mem_dup2\",\"metadata\":{\"tenant_id\":\"60\"}}";

        controller.handleWhopWebhook(body, null, null);

        verifyNoInteractions(subscriptionService);
        verify(subscriptionEventRepository, never()).save(any());
    }
}
