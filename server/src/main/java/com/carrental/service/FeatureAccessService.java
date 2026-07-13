package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final FeatureDefinitionRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final TenantFeatureOverrideRepository featureOverrideRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentTenantAccess() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        SubscriptionPlan plan = resolveTenantPlan(tenant);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantId", tenantId);
        response.put("planId", plan != null ? plan.getId() : null);
        response.put("planCode", plan != null ? plan.getCode() : null);
        response.put("planName", tenant.getPlanName());
        response.put("features", buildAccessRows(tenantId, plan));
        return response;
    }

    @Transactional(readOnly = true)
    public boolean isEnabledForCurrentTenant(String featureCode) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        SubscriptionPlan plan = resolveTenantPlan(tenant);
        return isEnabled(tenantId, plan, featureCode);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkCurrentTenantFeature(String featureCode) {
        Long tenantId = TenantContext.getCurrentTenantId();
        SubscriptionPlan plan = tenantRepository.findById(tenantId)
                .map(this::resolveTenantPlan)
                .orElse(null);
        FeatureDefinition feature = featureRepository.findByCode(featureCode).orElse(null);
        boolean enabled = isEnabled(tenantId, plan, featureCode);

        Map<String, Object> row = toAccessRow(featureCode, feature, enabled);
        row.put("currentPlan", plan != null ? plan.getName() : null);
        return row;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFeatureCatalog() {
        return featureRepository.findAll().stream().map(this::toFeatureMap).toList();
    }

    @Transactional
    public Map<String, Object> saveFeature(Map<String, Object> body) {
        String code = requireString(body, "code").trim().toUpperCase(Locale.ROOT);
        FeatureDefinition feature;
        if (body.get("id") != null) {
            Long id = Long.valueOf(body.get("id").toString());
            feature = featureRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found"));
        } else {
            feature = featureRepository.findByCode(code).orElseGet(FeatureDefinition::new);
        }
        feature.setCode(code);
        feature.setName((String) body.getOrDefault("name", humanize(code)));
        feature.setDescription((String) body.getOrDefault("description", ""));
        feature.setBenefits((String) body.getOrDefault("benefits", ""));
        feature.setCategory((String) body.getOrDefault("category", "Core"));
        feature.setActive((Boolean) body.getOrDefault("active", true));
        return toFeatureMap(featureRepository.save(feature));
    }

    @Transactional
    public void deleteFeature(Long id) {
        FeatureDefinition feature = featureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found"));
        planFeatureRepository.deleteAllByFeatureCode(feature.getCode());
        featureRepository.delete(feature);
    }

    @Transactional
    public Map<String, Object> assignFeatureToPlan(Long planId, String featureCode, boolean enabled) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        featureRepository.findByCode(featureCode)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found"));
        PlanFeature planFeature = planFeatureRepository.findByPlanIdAndFeatureCode(planId, featureCode)
                .orElseGet(() -> PlanFeature.builder().plan(plan).featureCode(featureCode).build());
        planFeature.setEnabled(enabled);
        PlanFeature saved = planFeatureRepository.save(planFeature);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", saved.getId());
        row.put("planId", plan.getId());
        row.put("planCode", plan.getCode());
        row.put("planName", plan.getName());
        row.put("featureCode", saved.getFeatureCode());
        row.put("enabled", saved.getEnabled());
        return row;
    }

    /**
     * Resolves a tenant's plan by matching {@link Tenant#getPlanName()} against
     * {@link SubscriptionPlan#getName()} (falling back to {@code code}).
     *
     * <p>{@code Tenant} has no {@code planId} foreign key — the link is a plain
     * string, so any drift between the two (a plan renamed, a tenant seeded with
     * a plan name that was never actually inserted into {@code subscription_plans})
     * makes this return {@code null} silently. Since every plan-gated feature
     * check treats "no plan" as "no premium features enabled" (see {@link #isEnabled}),
     * such drift disables ALL gated features for that tenant at once — including
     * ones the tenant is actually entitled to — with no error anywhere in the
     * request path. Log it loudly so this class of misconfiguration is
     * diagnosable instead of silently eating every feature check.
     */
    private SubscriptionPlan resolveTenantPlan(Tenant tenant) {
        if (tenant.getPlanName() == null) return null;
        Optional<SubscriptionPlan> plan = planRepository.findByName(tenant.getPlanName())
                .or(() -> planRepository.findByCode(tenant.getPlanName().toLowerCase(Locale.ROOT)));
        if (plan.isEmpty()) {
            log.warn("[FEATURE_ACCESS] Tenant id={} has planName='{}' which does not match any subscription_plans "
                            + "row (by name or code) — every plan-gated feature will resolve as disabled for this "
                            + "tenant until the plan name is corrected or the missing plan is seeded.",
                    tenant.getId(), tenant.getPlanName());
        }
        return plan.orElse(null);
    }

    private List<Map<String, Object>> buildAccessRows(Long tenantId, SubscriptionPlan plan) {
        Set<String> enabled = new HashSet<>();
        if (plan != null) {
            planFeatureRepository.findAllByPlanId(plan.getId()).stream()
                    .filter(pf -> Boolean.TRUE.equals(pf.getEnabled()))
                    .forEach(pf -> enabled.add(pf.getFeatureCode()));
        }
        LocalDateTime now = LocalDateTime.now();
        featureOverrideRepository
                .findAllByTenantIdAndEnabledTrueAndStartsAtLessThanEqualAndExpiresAtAfter(tenantId, now, now)
                .forEach(override -> enabled.add(override.getFeatureCode()));
        return featureRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(feature -> toAccessRow(feature.getCode(), feature, enabled.contains(feature.getCode())))
                .toList();
    }

    private boolean isEnabled(Long tenantId, SubscriptionPlan plan, String featureCode) {
        if (plan != null && planFeatureRepository
                .existsByPlanIdAndFeatureCodeAndEnabledTrue(plan.getId(), featureCode)) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        return featureOverrideRepository
                .existsByTenantIdAndFeatureCodeAndEnabledTrueAndStartsAtLessThanEqualAndExpiresAtAfter(
                        tenantId, featureCode, now, now);
    }

    private Map<String, Object> toAccessRow(String featureCode, FeatureDefinition feature, boolean enabled) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", featureCode);
        row.put("enabled", enabled);
        row.put("name", feature != null ? feature.getName() : humanize(featureCode));
        row.put("description", feature != null ? feature.getDescription() : "");
        row.put("benefits", feature != null ? feature.getBenefits() : "");
        row.put("category", feature != null ? feature.getCategory() : "");
        row.put("requiredPlans", requiredPlans(featureCode));
        row.put("requiredPlan", requiredPlans(featureCode).stream().findFirst().orElse("Premium"));
        return row;
    }

    private List<String> requiredPlans(String featureCode) {
        return planFeatureRepository.findAllByFeatureCodeAndEnabledTrue(featureCode).stream()
                .map(pf -> pf.getPlan().getName())
                .distinct()
                .toList();
    }

    private Map<String, Object> toFeatureMap(FeatureDefinition feature) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", feature.getId());
        row.put("code", feature.getCode());
        row.put("name", feature.getName());
        row.put("description", feature.getDescription());
        row.put("benefits", feature.getBenefits());
        row.put("category", feature.getCategory());
        row.put("active", feature.getActive());
        return row;
    }

    private String requireString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.toString();
    }

    private String humanize(String code) {
        String lower = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Arrays.stream(lower.split(" "))
                .map(word -> word.isBlank() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
