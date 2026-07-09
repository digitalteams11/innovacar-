package com.carrental.controller;

import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.FeatureAccessService;
import com.carrental.service.PlanLimitService;
import com.carrental.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Agency-facing billing endpoints.
 *
 * POST /api/billing/promo/validate  â€” validate & compute promo discount
 * POST /api/billing/checkout        â€” create Whop checkout session and return URL
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final SubscriptionPlanRepository    planRepository;
    private final PromoCodeRepository           promoCodeRepository;
    private final PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    private final PromoCodePlanLinkRepository   promoCodePlanLinkRepository;
    private final TenantRepository              tenantRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final SubscriptionService           subscriptionService;
    private final FeatureAccessService          featureAccessService;
    private final PlanLimitService              planLimitService;

    @Value("${whop.api.key:}")
    private String whopApiKey;

    @Value("${whop.api.base-url:https://api.whop.com}")
    private String whopBaseUrl;

    // â”€â”€ GET /api/billing/access â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Returns the current tenant's plan access matrix (features + usage limits).
     * Used by the frontend usePlanAccess hook to render locked states and usage bars.
     */
    @GetMapping("/access")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getPlanAccess() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantId != null ? tenantRepository.findById(tenantId).orElse(null) : null;
        String planName   = tenant != null ? tenant.getPlanName()  : null;
        String planCode   = planName  != null ? planName.toUpperCase(java.util.Locale.ROOT) : null;
        String subStatus  = tenant != null ? tenant.getStatus() : "UNKNOWN";

        // â”€â”€ Feature access matrix â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Map<String, Object> rawAccess = featureAccessService.getCurrentTenantAccess();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> featureList =
                (java.util.List<Map<String, Object>>) rawAccess.get("features");

        // Map internal codes to user-facing keys and build boolean map
        Map<String, String> CODE_MAP = Map.ofEntries(
                Map.entry("VEHICLE_MANAGEMENT",     "VEHICLES"),
                Map.entry("CLIENT_MANAGEMENT",      "CLIENTS"),
                Map.entry("RESERVATION_MANAGEMENT", "RESERVATIONS"),
                Map.entry("CONTRACT_MANAGEMENT",    "CONTRACTS"),
                Map.entry("INVOICE_GENERATION",     "INVOICES"),
                Map.entry("MULTI_EMPLOYEE",         "EMPLOYEES"),
                Map.entry("MULTI_BRANCH",           "MULTI_BRANCH"),
                Map.entry("REPORTS_BASIC",          "BASIC_REPORTS")
        );

        Map<String, Boolean> features = new LinkedHashMap<>();
        if (featureList != null) {
            for (Map<String, Object> f : featureList) {
                String code    = (String)  f.get("code");
                boolean enabled = Boolean.TRUE.equals(f.get("enabled"));
                String key = CODE_MAP.getOrDefault(code, code);
                features.put(key, enabled);
            }
        }

        // â”€â”€ Usage limits â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Map<String, Object> usage = planLimitService.getUsageSummary();

        // Resolve limits from plan
        SubscriptionPlan plan = planCode != null
                ? planRepository.findByCode(planCode).or(() -> planRepository.findByName(planName)).orElse(null)
                : null;

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("vehicles",     resourceLimit(
                toLong(usage.get("vehiclesUsed")),    toInt(usage.get("vehiclesLimit"))));
        limits.put("employees",    resourceLimit(
                toLong(usage.get("employeesUsed")),   toInt(usage.get("employeesLimit"))));
        limits.put("gpsDevices",   resourceLimit(
                toLong(usage.get("gpsDevicesUsed")),  toInt(usage.get("gpsDevicesLimit"))));
        limits.put("reservations", resourceLimit(
                toLong(usage.get("reservationsUsed")),toInt(usage.get("reservationsLimit"))));
        limits.put("clients",      resourceLimit(
                toLong(usage.get("clientsUsed")),     toInt(usage.get("clientsLimit"))));
        limits.put("contracts",    resourceLimit(
                toLong(usage.get("contractsUsed")),   toInt(usage.get("contractsLimit"))));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentPlan",         planName);
        data.put("currentPlanCode",      planCode);
        data.put("subscriptionStatus",   subStatus);
        data.put("features",             features);
        data.put("limits",               limits);

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    private static Map<String, Object> resourceLimit(long used, int limit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("used",  used);
        m.put("limit", limit);
        return m;
    }
    private static long toLong(Object o) { return o instanceof Number n ? n.longValue() : 0L; }
    private static int  toInt(Object o)  { return o instanceof Number n ? n.intValue()  : 0; }

    // â”€â”€ POST /api/billing/promo/validate â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/promo/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> validatePromo(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant  = tenantId != null ? tenantRepository.findById(tenantId).orElse(null) : null;

        String code    = Objects.toString(body.get("code"), "").trim().toUpperCase(Locale.ROOT);
        Long   planId  = body.get("planId")   != null ? Long.valueOf(body.get("planId").toString())   : null;
        String planCode= Objects.toString(body.getOrDefault("planCode", ""), "").trim().toUpperCase(Locale.ROOT);
        String cycle   = Objects.toString(body.getOrDefault("billingCycle", "MONTHLY"), "MONTHLY").toUpperCase(Locale.ROOT);

        if (code.isBlank()) {
            return promoError("PROMO_CODE_REQUIRED", "Promo code is required.");
        }

        SubscriptionPlan plan = planId != null
                ? planRepository.findById(planId).orElse(null)
                : (!planCode.isBlank() ? planRepository.findByCode(planCode).orElse(null) : null);

        PromoCode promo = promoCodeRepository.findByCodeAndDeletedFalse(code).orElse(null);

        if (promo == null) {
            return promoError("PROMO_CODE_NOT_FOUND", "Promo code not found.");
        }
        if (!Boolean.TRUE.equals(promo.getIsActive())) {
            return promoError("PROMO_CODE_INACTIVE", "This promo code is not active.");
        }

        LocalDate today = LocalDate.now();
        if (promo.getValidFrom() != null && today.isBefore(promo.getValidFrom())) {
            return promoError("PROMO_CODE_NOT_STARTED", "This promo code is not yet valid.");
        }
        if (promo.getValidTo() != null && today.isAfter(promo.getValidTo())) {
            return promoError("PROMO_CODE_EXPIRED", "This promo code has expired.");
        }

        // Global redemption limit (count USED redemptions, not RESERVED)
        if (promo.getMaxUses() != null) {
            long usedCount = promoCodeRedemptionRepository.countByPromoCodeIdAndStatus(promo.getId(), "USED");
            if (usedCount >= promo.getMaxUses()) {
                return promoError("PROMO_CODE_LIMIT_REACHED", "This promo code has reached its maximum usage limit.");
            }
        }

        // Per-agency limit
        if (promo.getMaxUsesPerAgency() != null && tenantId != null) {
            long agencyUsed = promoCodeRedemptionRepository.countByPromoCodeIdAndTenantIdAndStatus(promo.getId(), tenantId, "USED");
            if (agencyUsed >= promo.getMaxUsesPerAgency()) {
                return promoError("PROMO_CODE_AGENCY_LIMIT_REACHED", "You have already used this promo code the maximum allowed times.");
            }
        }

        // Plan eligibility (skip if appliesToAllPlans)
        if (!Boolean.TRUE.equals(promo.getAppliesToAllPlans())
                && promo.getApplicablePlans() != null && !promo.getApplicablePlans().isBlank()
                && plan != null) {
            boolean eligible = Arrays.stream(promo.getApplicablePlans().split(","))
                    .map(String::trim)
                    .anyMatch(v -> v.equalsIgnoreCase(plan.getCode()) || v.equalsIgnoreCase(plan.getName()));
            if (!eligible) {
                return promoError("PROMO_CODE_PLAN_NOT_ALLOWED", "This promo code does not apply to the selected plan.");
            }
        }

        // Billing cycle restriction
        if (promo.getBillingCycle() != null && !"BOTH".equalsIgnoreCase(promo.getBillingCycle())
                && !promo.getBillingCycle().equalsIgnoreCase(cycle)) {
            return promoError("PROMO_CODE_BILLING_CYCLE_NOT_ALLOWED",
                    "This promo code is only valid for " + promo.getBillingCycle().toLowerCase(Locale.ROOT) + " billing.");
        }

        // First-time only
        if (Boolean.TRUE.equals(promo.getFirstTimeOnly()) && tenant != null) {
            boolean isFirstTime = "TRIAL".equalsIgnoreCase(tenant.getStatus())
                    || "TRIAL".equalsIgnoreCase(tenant.getPlanName());
            if (!isFirstTime) {
                return promoError("PROMO_CODE_FIRST_TIME_ONLY", "This promo code is for first-time subscribers only.");
            }
        }

        // Compute amounts
        BigDecimal originalPrice = BigDecimal.ZERO;
        if (plan != null) {
            originalPrice = "YEARLY".equalsIgnoreCase(cycle) ? plan.getYearlyPrice() : plan.getMonthlyPrice();
            if (originalPrice == null) originalPrice = BigDecimal.ZERO;
        }

        // Minimum amount check
        if (promo.getMinimumAmount() != null && originalPrice.compareTo(promo.getMinimumAmount()) < 0) {
            return promoError("PROMO_CODE_MINIMUM_AMOUNT_NOT_MET",
                    "This promo code requires a minimum amount of " + promo.getMinimumAmount().toPlainString() + " MAD.");
        }

        BigDecimal discountAmount = computeDiscount(promo, originalPrice);
        BigDecimal finalPrice = originalPrice.subtract(discountAmount).max(BigDecimal.ZERO);
        finalPrice    = finalPrice.setScale(2, RoundingMode.HALF_UP);
        discountAmount = discountAmount.setScale(2, RoundingMode.HALF_UP);

        // Check if a discounted Whop checkout link is configured (Mode 1)
        boolean checkoutConfigured = false;
        if (plan != null) {
            checkoutConfigured = promoCodePlanLinkRepository
                    .findByPromoCodeIdAndPlanCodeIgnoreCaseAndBillingCycleIgnoreCaseAndActiveTrue(
                            promo.getId(), plan.getCode(), cycle)
                    .map(link -> link.getWhopCheckoutUrlOverride() != null && !link.getWhopCheckoutUrlOverride().isBlank())
                    .orElse(false);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", true);
        data.put("promoCodeId", promo.getId());
        data.put("code", promo.getCode());
        data.put("discountType", buildDiscountType(promo));
        data.put("discountValue", promo.getDiscountValue() != null ? promo.getDiscountValue() : 0);
        data.put("freeMonths", promo.getFreeMonths() != null ? promo.getFreeMonths() : 0);
        data.put("originalAmount", originalPrice);
        data.put("originalPrice", originalPrice);
        data.put("discountAmount", discountAmount);
        data.put("finalAmount", finalPrice);
        data.put("finalPrice", finalPrice);
        data.put("currency", plan != null && plan.getCurrency() != null ? plan.getCurrency() : "MAD");
        data.put("message", buildPromoMessage(promo, discountAmount, finalPrice));
        data.put("checkoutConfigured", checkoutConfigured);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // â”€â”€ POST /api/billing/checkout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> createCheckout(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        Long planId    = body.get("planId") != null ? Long.valueOf(body.get("planId").toString()) : null;
        String planCode = Objects.toString(body.getOrDefault("planCode", ""), "");
        String cycle   = Objects.toString(body.getOrDefault("billingCycle", "MONTHLY"), "MONTHLY").toUpperCase(Locale.ROOT);
        String promoCode = Objects.toString(body.getOrDefault("promoCode", ""), "").trim();

        // Resolve plan
        SubscriptionPlan plan = planId != null
                ? planRepository.findById(planId).orElse(null)
                : planRepository.findByCode(planCode).orElse(null);

        if (plan == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Plan not found."));
        }
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This plan is no longer available."));
        }

        // Billing cycle availability
        if ("MONTHLY".equals(cycle) && Boolean.FALSE.equals(plan.getBillingCycleAllowedMonthly())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Monthly billing is not available for this plan."));
        }
        if ("YEARLY".equals(cycle) && Boolean.FALSE.equals(plan.getBillingCycleAllowedYearly())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Yearly billing is not available for this plan."));
        }

        // Validate promo code if provided
        PromoCode promo = null;
        if (!promoCode.isBlank()) {
            promo = promoCodeRepository.findByCodeAndDeletedFalse(promoCode.toUpperCase(Locale.ROOT)).orElse(null);
            if (promo == null || !Boolean.TRUE.equals(promo.getIsActive())) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "errorCode", "PROMO_CODE_INACTIVE", "message", "Invalid or inactive promo code."));
            }
            LocalDate today = LocalDate.now();
            if (promo.getValidTo() != null && today.isAfter(promo.getValidTo())) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "errorCode", "PROMO_CODE_EXPIRED", "message", "This promo code has expired."));
            }
        }

        // Compute final price
        BigDecimal originalPrice = "YEARLY".equalsIgnoreCase(cycle) ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        if (originalPrice == null) originalPrice = BigDecimal.ZERO;
        BigDecimal discountAmount = promo != null ? computeDiscount(promo, originalPrice) : BigDecimal.ZERO;
        BigDecimal finalPrice = originalPrice.subtract(discountAmount).max(BigDecimal.ZERO);

        // A promo-specific Whop URL is an optional override. If it is missing,
        // use the normal plan checkout URL and keep promo context as metadata.
        String promoOverrideUrl = null;
        if (promo != null) {
            promoOverrideUrl = promoCodePlanLinkRepository
                    .findByPromoCodeIdAndPlanCodeIgnoreCaseAndBillingCycleIgnoreCaseAndActiveTrue(
                            promo.getId(), plan.getCode(), cycle)
                    .map(PromoCodePlanLink::getWhopCheckoutUrlOverride)
                    .orElse(null);
            if (promoOverrideUrl != null && promoOverrideUrl.isBlank()) promoOverrideUrl = null;
            if (promoOverrideUrl == null) {
                log.info("[BILLING_CHECKOUT] Promo {} has no dedicated checkout URL for plan={} cycle={}; using normal checkout URL.",
                        promo.getCode(), plan.getCode(), cycle);
            }
        }

        String checkoutUrl = promoOverrideUrl != null
                ? appendCheckoutMetadata(promoOverrideUrl, tenantId, plan.getCode(), cycle, promoCode)
                : resolveCheckoutUrl(plan, cycle, tenantId, promo != null ? promoCode : null);

        boolean apiKeyPresent = whopApiKey != null && !whopApiKey.isBlank();
        boolean hasStaticUrl = ("YEARLY".equals(cycle) ? plan.getWhopCheckoutUrlYearly() : plan.getWhopCheckoutUrlMonthly()) != null
                && !("YEARLY".equals(cycle) ? plan.getWhopCheckoutUrlYearly() : plan.getWhopCheckoutUrlMonthly()).isBlank();
        boolean hasDynamicId = plan.getWhopPlanId() != null && !plan.getWhopPlanId().isBlank();
        log.info("[WHOP_CHECKOUT_DEBUG] agencyId={} planCode={} planId={} whopPlanId={} whopCheckoutLinkPresent={} apiKeyPresent={} checkoutMode={}",
                tenantId, plan.getCode(), plan.getId(),
                plan.getWhopPlanId(), hasStaticUrl, apiKeyPresent,
                apiKeyPresent && hasDynamicId ? "API_CREATE" : hasStaticUrl ? "STORED_LINK" : "NONE");

        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            String missingField = "YEARLY".equals(cycle) ? "whopCheckoutUrlYearly" : "whopCheckoutUrlMonthly";
            Map<String, Object> errData = new LinkedHashMap<>();
            errData.put("planCode", plan.getCode());
            errData.put("billingCycle", cycle);
            errData.put("missingField", missingField);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "errorCode", "PLAN_CHECKOUT_URL_MISSING",
                    "message", "Checkout URL is missing for " + plan.getName() + " "
                            + cycle.toLowerCase(Locale.ROOT) + " plan. Configure Whop checkout URL in Super Admin.",
                    "data", errData));
        }

        log.info("[BILLING_CHECKOUT] tenantId={} plan={} cycle={} promo={} price={}",
                tenantId, plan.getCode(), cycle, promoCode, finalPrice);

        // Record RESERVED redemption so we can enforce per-agency limits before payment completes
        if (promo != null) {
            PromoCodeRedemption reservation = new PromoCodeRedemption();
            reservation.setPromoCode(promo);
            reservation.setTenant(tenant);
            reservation.setPlanCode(plan.getCode());
            reservation.setBillingCycle(cycle);
            reservation.setOriginalPrice(originalPrice);
            reservation.setDiscountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP));
            reservation.setFinalPrice(finalPrice.setScale(2, RoundingMode.HALF_UP));
            reservation.setStatus("RESERVED");
            reservation.setWhopCheckoutUrl(checkoutUrl);
            promoCodeRedemptionRepository.save(reservation);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Checkout created successfully.");
        result.put("checkoutUrl", checkoutUrl);
        result.put("planCode", plan.getCode());
        result.put("planName", plan.getName());
        result.put("billingCycle", cycle);
        result.put("subtotal", originalPrice);
        result.put("originalPrice", originalPrice);
        result.put("discountAmount", discountAmount);
        result.put("total", finalPrice);
        result.put("finalPrice", finalPrice);
        result.put("promoCode", promo != null ? promoCode : null);
        result.put("currency", plan.getCurrency() != null ? plan.getCurrency() : "MAD");
        return ResponseEntity.ok(result);
    }

    // â”€â”€ POST /api/billing/refresh-status â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Called by the frontend when the user returns from the Whop checkout page
     * (e.g. after ?payment=success in the URL).  Re-reads the tenant's current
     * subscription state from the DB and returns a fresh status snapshot so the
     * UI can show the activated plan without waiting for the next page load.
     *
     * Activation itself comes from the Whop webhook â€” this endpoint only reads
     * the current state; it never assumes payment succeeded.
     */
    @PostMapping("/refresh-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> refreshStatus() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tenant not found."));
        }

        log.info("[BILLING_REFRESH] tenantId={} status={} plan={} subscriptionActive={}",
                tenantId, tenant.getStatus(), tenant.getPlanName(), tenant.isSubscriptionActive());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Subscription status refreshed.");
        result.put("status", tenant.getStatus());
        result.put("planName", tenant.getPlanName());
        result.put("subscriptionActive", tenant.isSubscriptionValid());
        result.put("subscriptionEndDate", tenant.getSubscriptionEndDate());
        result.put("isTrial", "TRIAL".equalsIgnoreCase(tenant.getPlanName())
                || "TRIAL".equalsIgnoreCase(tenant.getStatus()));
        return ResponseEntity.ok(result);
    }

    // â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Resolves the checkout URL for a plan.
     * Priority:
     *   1. Dynamic Whop checkout link via API (if WHOP_API_KEY is set + whopPlanId configured)
     *   2. Static checkout URL from plan record (whopCheckoutUrlMonthly/Yearly)
     */
    private String resolveCheckoutUrl(SubscriptionPlan plan, String cycle, Long tenantId, String promoCode) {
        boolean yearly = "YEARLY".equalsIgnoreCase(cycle);

        // Try dynamic Whop API checkout creation
        if (whopApiKey != null && !whopApiKey.isBlank()) {
            String whopPlanId = plan.getWhopPlanId();
            // For yearly, prefer whopPriceId; for monthly use whopPlanId
            String whopProductId = plan.getWhopProductId();
            if (yearly && plan.getWhopPriceId() != null && !plan.getWhopPriceId().isBlank()) {
                whopPlanId = plan.getWhopPriceId();
            }
            if (whopPlanId != null && !whopPlanId.isBlank()) {
                try {
                    return createWhopCheckoutLink(whopPlanId, tenantId, promoCode);
                } catch (Exception e) {
                    log.warn("[WHOP_CHECKOUT] Failed to create dynamic checkout for plan={}: {}", plan.getCode(), e.getMessage());
                    // Fall through to static URL
                }
            }
        }

        // Fall back to static checkout URL configured by Super Admin
        String staticUrl = yearly ? plan.getWhopCheckoutUrlYearly() : plan.getWhopCheckoutUrlMonthly();
        if (staticUrl != null && !staticUrl.isBlank()) {
            return appendCheckoutMetadata(staticUrl, tenantId, plan.getCode(), cycle, promoCode);
        }

        return null;
    }

    private String appendCheckoutMetadata(String checkoutUrl, Long tenantId, String planCode, String cycle, String promoCode) {
        if (checkoutUrl == null || checkoutUrl.isBlank()) return checkoutUrl;

        Map<String, String> metadata = new LinkedHashMap<>();
        if (tenantId != null) metadata.put("metadata[tenant_id]", tenantId.toString());
        if (planCode != null && !planCode.isBlank()) metadata.put("metadata[plan_code]", planCode);
        if (cycle != null && !cycle.isBlank()) metadata.put("metadata[billing_cycle]", cycle);
        if (promoCode != null && !promoCode.isBlank()) metadata.put("metadata[promo_code]", promoCode);
        if (metadata.isEmpty()) return checkoutUrl;

        StringJoiner query = new StringJoiner("&");
        metadata.forEach((key, value) -> query.add(urlEncode(key) + "=" + urlEncode(value)));
        String separator = checkoutUrl.contains("?") ? "&" : "?";
        return checkoutUrl + separator + query;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Calls Whop API v2 to create a checkout link with tenant metadata.
     * See: https://dev.whop.com/api-reference/v5/memberships/create-membership
     */
    private String createWhopCheckoutLink(String whopPlanId, Long tenantId, String promoCode) throws Exception {
        String requestBody = buildWhopCheckoutBody(whopPlanId, tenantId, promoCode);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(whopBaseUrl + "/api/v2/memberships"))
                .header("Authorization", "Bearer " + whopApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        log.debug("[WHOP_API] checkout response status={} body={}", response.statusCode(), response.body());

        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Whop API returned " + response.statusCode() + ": " + response.body());
        }

        // Parse checkout_url from response JSON (minimal parsing to avoid extra deps)
        String body = response.body();
        return extractJsonString(body, "checkout_url");
    }

    private String buildWhopCheckoutBody(String whopPlanId, Long tenantId, String promoCode) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"plan_id\":\"").append(whopPlanId).append("\"");
        sb.append(",\"metadata\":{\"tenant_id\":\"").append(tenantId).append("\"");
        if (promoCode != null && !promoCode.isBlank()) {
            sb.append(",\"promo_code\":\"").append(promoCode).append("\"");
        }
        sb.append("}");
        if (promoCode != null && !promoCode.isBlank()) {
            sb.append(",\"discount_code\":\"").append(promoCode).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /** Minimal JSON string extractor for a single key. */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    private BigDecimal computeDiscount(PromoCode promo, BigDecimal subtotal) {
        if (promo == null || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        String type = Optional.ofNullable(promo.getPromotionType()).orElse("DISCOUNT").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "PLAN_TRIAL" -> subtotal; // full discount â€” gets trial plan
            case "FREE_MONTHS" -> BigDecimal.ZERO; // price unchanged, extra months added later
            case "DISCOUNT" -> {
                if ("PERCENTAGE".equalsIgnoreCase(promo.getDiscountType())) {
                    BigDecimal pct = promo.getDiscountValue() != null ? promo.getDiscountValue() : BigDecimal.ZERO;
                    yield subtotal.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                } else {
                    yield promo.getDiscountValue() != null ? promo.getDiscountValue().min(subtotal) : BigDecimal.ZERO;
                }
            }
            default -> BigDecimal.ZERO;
        };
    }

    private String buildDiscountType(PromoCode promo) {
        String type = Optional.ofNullable(promo.getPromotionType()).orElse("DISCOUNT").toUpperCase(Locale.ROOT);
        if ("DISCOUNT".equals(type)) {
            return "PERCENTAGE".equalsIgnoreCase(promo.getDiscountType()) ? "PERCENT_OFF" : "AMOUNT_OFF";
        }
        return type;
    }

    private String buildPromoMessage(PromoCode promo, BigDecimal discountAmount, BigDecimal finalPrice) {
        String type = Optional.ofNullable(promo.getPromotionType()).orElse("DISCOUNT").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "FREE_MONTHS" -> (promo.getFreeMonths() != null ? promo.getFreeMonths() : 1)
                    + " free month(s) added to your subscription.";
            case "PLAN_TRIAL" -> "Special trial plan applied.";
            case "FREE_FEATURE" -> "Special feature unlocked: " + promo.getFreeFeatureCode();
            default -> {
                if ("PERCENTAGE".equalsIgnoreCase(promo.getDiscountType()) && promo.getDiscountValue() != null) {
                    yield promo.getDiscountValue().toPlainString() + "% discount applied. Final price: "
                            + finalPrice.toPlainString() + " MAD.";
                } else {
                    yield discountAmount.toPlainString() + " MAD discount applied. Final price: "
                            + finalPrice.toPlainString() + " MAD.";
                }
            }
        };
    }

    // â”€â”€ GET /api/billing/plans â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> getPlansForBilling() {
        List<SubscriptionPlan> plans = planRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
        boolean apiKeyPresent = whopApiKey != null && !whopApiKey.isBlank();

        List<Map<String, Object>> planList = plans.stream().map(plan -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", plan.getId());
            p.put("code", plan.getCode());
            p.put("name", plan.getName());
            p.put("description", plan.getDescription());
            p.put("monthlyPrice", plan.getMonthlyPrice());
            p.put("yearlyPrice", plan.getYearlyPrice());
            p.put("currency", plan.getCurrency() != null ? plan.getCurrency() : "MAD");
            p.put("vehicleLimit", plan.getMaxVehicles());
            p.put("maxVehicles", plan.getMaxVehicles());
            p.put("employeeLimit", plan.getMaxEmployees());
            p.put("maxEmployees", plan.getMaxEmployees());
            p.put("gpsLimit", plan.getMaxGpsDevices());
            p.put("maxGpsDevices", plan.getMaxGpsDevices());
            p.put("reservationLimit", plan.getMaxReservations());
            p.put("maxReservations", plan.getMaxReservations());
            p.put("contractLimit", plan.getContractLimit());
            p.put("clientLimit", plan.getClientLimit());
            p.put("storageLimitMb", plan.getStorageLimitMb());
            p.put("trialDays", plan.getTrialDays() != null ? plan.getTrialDays() : 0);
            p.put("apiAccess", Boolean.TRUE.equals(plan.getApiAccess()));
            p.put("whiteLabel", Boolean.TRUE.equals(plan.getWhiteLabel()));
            p.put("prioritySupport", Boolean.TRUE.equals(plan.getPrioritySupport()));
            p.put("highlighted", Boolean.TRUE.equals(plan.getHighlighted()));
            p.put("active", Boolean.TRUE.equals(plan.getIsActive()));
            p.put("sortOrder", plan.getDisplayOrder());
            p.put("billingCycleAllowedMonthly", !Boolean.FALSE.equals(plan.getBillingCycleAllowedMonthly()));
            p.put("billingCycleAllowedYearly", !Boolean.FALSE.equals(plan.getBillingCycleAllowedYearly()));
            p.put("features", parseFeaturesJson(plan.getFeaturesJson()));

            boolean isFree = plan.getMonthlyPrice() == null
                    || plan.getMonthlyPrice().compareTo(BigDecimal.ZERO) == 0;
            boolean hasMonthlyUrl = plan.getWhopCheckoutUrlMonthly() != null && !plan.getWhopCheckoutUrlMonthly().isBlank();
            boolean hasYearlyUrl = plan.getWhopCheckoutUrlYearly() != null && !plan.getWhopCheckoutUrlYearly().isBlank();
            boolean hasDynamicId = plan.getWhopPlanId() != null && !plan.getWhopPlanId().isBlank() && apiKeyPresent;
            boolean checkoutConfigured = isFree || hasMonthlyUrl || hasYearlyUrl || hasDynamicId;
            p.put("isFree", isFree);
            p.put("checkoutConfigured", checkoutConfigured);

            Map<String, Boolean> modules = new LinkedHashMap<>();
            modules.put("contracts", true);
            modules.put("reservations", true);
            modules.put("clients", true);
            modules.put("employees", true);
            modules.put("gps", plan.getMaxGpsDevices() != null && plan.getMaxGpsDevices() > 0);
            modules.put("ai", Boolean.TRUE.equals(plan.getApiAccess()));
            modules.put("advancedReports", plan.getMaxVehicles() != null && plan.getMaxVehicles() >= 30);
            modules.put("customTemplates", plan.getMaxVehicles() != null && plan.getMaxVehicles() >= 30);
            modules.put("whiteLabel", Boolean.TRUE.equals(plan.getWhiteLabel()));
            modules.put("prioritySupport", Boolean.TRUE.equals(plan.getPrioritySupport()));
            p.put("includedModules", modules);
            return p;
        }).toList();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getName() : null;
        String role = (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty())
                ? auth.getAuthorities().iterator().next().getAuthority() : null;
        Long agencyId = null;
        try { agencyId = TenantContext.getCurrentTenantId(); } catch (Exception ignored) {}
        long totalInDb = planRepository.count();

        log.info("[BILLING_PLANS_API_DEBUG] userId={} agencyId={} role={} totalPlans={} activePlans={} returnedPlans={}",
                userId, agencyId, role, totalInDb, plans.size(), planList.size());

        return ResponseEntity.ok(Map.of("success", true, "data", planList));
    }

    // â”€â”€ GET /api/billing/current â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCurrentSubscription() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tenant not found."));
        }
        SubscriptionPlan plan = planRepository.findByName(tenant.getPlanName())
                .or(() -> planRepository.findByCode(tenant.getPlanName()))
                .orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agencyId", tenantId);
        data.put("planCode", plan != null ? plan.getCode() : tenant.getPlanName());
        data.put("planName", tenant.getPlanName());
        data.put("status", tenant.getStatus());
        data.put("subscriptionActive", tenant.isSubscriptionValid());
        data.put("renewsAt", tenant.getSubscriptionEndDate());
        data.put("cancelAtPeriodEnd", false);
        data.put("limits", Map.of(
                "vehicles", plan != null && plan.getMaxVehicles() != null ? plan.getMaxVehicles() : (tenant.getMaxVehicles() != null ? tenant.getMaxVehicles() : 0),
                "employees", plan != null && plan.getMaxEmployees() != null ? plan.getMaxEmployees() : (tenant.getMaxEmployees() != null ? tenant.getMaxEmployees() : 0),
                "gpsDevices", plan != null && plan.getMaxGpsDevices() != null ? plan.getMaxGpsDevices() : (tenant.getMaxGpsDevices() != null ? tenant.getMaxGpsDevices() : 0),
                "reservations", plan != null && plan.getMaxReservations() != null ? plan.getMaxReservations() : (tenant.getMaxReservations() != null ? tenant.getMaxReservations() : 0)
        ));
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    private List<String> parseFeaturesJson(String featuresJson) {
        if (featuresJson == null || featuresJson.isBlank()) return List.of();
        try {
            String s = featuresJson.trim();
            if (s.startsWith("[")) {
                s = s.substring(1, s.length() - 1);
                return Arrays.stream(s.split(","))
                        .map(item -> item.trim().replaceAll("^\"|\"$", ""))
                        .filter(item -> !item.isBlank())
                        .toList();
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    private ResponseEntity<Map<String, Object>> promoError(String errorCode, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", false);
        data.put("errorCode", errorCode);
        data.put("message", message);
        return ResponseEntity.ok(Map.of("success", false, "data", data));
    }
}




