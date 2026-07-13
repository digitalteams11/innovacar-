package com.carrental.service;

import com.carrental.entity.SubscriptionPlan;
import com.carrental.entity.Tenant;
import com.carrental.repository.FeatureDefinitionRepository;
import com.carrental.repository.PlanFeatureRepository;
import com.carrental.repository.SubscriptionPlanRepository;
import com.carrental.repository.TenantFeatureOverrideRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the "AI Assistant blocked despite an entitled
 * plan" bug: a tenant whose {@code planName} does not match any row in
 * {@code subscription_plans} (e.g. the "Enterprise" plan referenced by
 * DataInitializer's system tenant before it was seeded — see
 * V52__seed_enterprise_plan.sql) must resolve to "feature disabled", not
 * throw, and must be diagnosable — never silently indistinguishable from a
 * tenant that is correctly on a plan without the feature.
 */
@ExtendWith(MockitoExtension.class)
class FeatureAccessServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private FeatureDefinitionRepository featureRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private TenantFeatureOverrideRepository featureOverrideRepository;

    @InjectMocks private FeatureAccessService featureAccessService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void isEnabledForCurrentTenant_planHasFeatureEnabled_returnsTrue() {
        TenantContext.setCurrentTenantId(1L);
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setPlanName("Premium");
        SubscriptionPlan plan = SubscriptionPlan.builder().id(10L).name("Premium").code("premium").build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(planRepository.findByName("Premium")).thenReturn(Optional.of(plan));
        when(planFeatureRepository.existsByPlanIdAndFeatureCodeAndEnabledTrue(10L, "AI_ASSISTANT")).thenReturn(true);

        assertThat(featureAccessService.isEnabledForCurrentTenant("AI_ASSISTANT")).isTrue();
    }

    /**
     * The exact regression scenario: planName references a plan that doesn't
     * exist in subscription_plans. Must resolve to "disabled" (safe default,
     * never a bypass) rather than throwing — but this must be a data problem
     * an operator can diagnose, not an indistinguishable dead end.
     */
    @Test
    void isEnabledForCurrentTenant_unresolvablePlanName_returnsFalseWithoutThrowing() {
        TenantContext.setCurrentTenantId(2L);
        Tenant tenant = new Tenant();
        tenant.setId(2L);
        tenant.setPlanName("Enterprise");

        when(tenantRepository.findById(2L)).thenReturn(Optional.of(tenant));
        when(planRepository.findByName("Enterprise")).thenReturn(Optional.empty());
        when(planRepository.findByCode("enterprise")).thenReturn(Optional.empty());
        when(featureOverrideRepository.existsByTenantIdAndFeatureCodeAndEnabledTrueAndStartsAtLessThanEqualAndExpiresAtAfter(
                eq(2L), eq("AI_ASSISTANT"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);

        assertThat(featureAccessService.isEnabledForCurrentTenant("AI_ASSISTANT")).isFalse();
    }

    @Test
    void isEnabledForCurrentTenant_planLacksFeatureButActiveOverrideExists_returnsTrue() {
        TenantContext.setCurrentTenantId(3L);
        Tenant tenant = new Tenant();
        tenant.setId(3L);
        tenant.setPlanName("Standard");
        SubscriptionPlan plan = SubscriptionPlan.builder().id(20L).name("Standard").code("standard").build();

        when(tenantRepository.findById(3L)).thenReturn(Optional.of(tenant));
        when(planRepository.findByName("Standard")).thenReturn(Optional.of(plan));
        when(planFeatureRepository.existsByPlanIdAndFeatureCodeAndEnabledTrue(20L, "AI_ASSISTANT")).thenReturn(false);
        when(featureOverrideRepository.existsByTenantIdAndFeatureCodeAndEnabledTrueAndStartsAtLessThanEqualAndExpiresAtAfter(
                eq(3L), eq("AI_ASSISTANT"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);

        assertThat(featureAccessService.isEnabledForCurrentTenant("AI_ASSISTANT")).isTrue();
    }
}
