package com.carrental.controller;

import com.carrental.entity.PromoCode;
import com.carrental.entity.PaymentGatewayConfig;
import com.carrental.entity.AffiliateRule;
import com.carrental.entity.AffiliateReferral;
import com.carrental.entity.AffiliateConversion;
import com.carrental.repository.AffiliateConversionRepository;
import com.carrental.repository.AffiliateRuleRepository;
import com.carrental.repository.AffiliateReferralRepository;
import com.carrental.repository.PaymentGatewayConfigRepository;
import com.carrental.repository.PlanFeatureRepository;
import com.carrental.repository.PromoCodeRepository;
import com.carrental.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/features")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminFeatureController {

    private final FeatureAccessService featureAccessService;
    private final PromoCodeRepository promoCodeRepository;
    private final PaymentGatewayConfigRepository gatewayConfigRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AffiliateRuleRepository affiliateRuleRepository;
    private final AffiliateReferralRepository affiliateReferralRepository;
    private final AffiliateConversionRepository affiliateConversionRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listFeatures() {
        return ResponseEntity.ok(featureAccessService.getFeatureCatalog());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createFeature(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(featureAccessService.saveFeature(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateFeature(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return ResponseEntity.ok(featureAccessService.saveFeature(body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteFeature(@PathVariable Long id) {
        featureAccessService.deleteFeature(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/plans/{planId}/{featureCode}")
    public ResponseEntity<Map<String, Object>> assignFeature(
            @PathVariable Long planId,
            @PathVariable String featureCode,
            @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(featureAccessService.assignFeatureToPlan(planId, featureCode, enabled));
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<?> getPlanFeatures(@PathVariable Long planId) {
        return ResponseEntity.ok(planFeatureRepository.findAllByPlanId(planId).stream().map(planFeature -> Map.of(
                "id", planFeature.getId(),
                "featureCode", planFeature.getFeatureCode(),
                "enabled", Boolean.TRUE.equals(planFeature.getEnabled())
        )).toList());
    }

    @GetMapping("/promotions")
    public ResponseEntity<List<PromoCode>> listPromotions() {
        return ResponseEntity.ok(promoCodeRepository.findAll());
    }

    @PostMapping("/promotions")
    public ResponseEntity<PromoCode> savePromotion(@RequestBody PromoCode promo) {
        if (promo.getUsedCount() == null) promo.setUsedCount(0);
        return ResponseEntity.ok(promoCodeRepository.save(promo));
    }

    @PostMapping("/promotions/defaults")
    public ResponseEntity<Map<String, Object>> seedDefaultPromotions() {
        savePromoIfMissing("WELCOME20", "PERCENTAGE", new BigDecimal("20"), "trial,basic,standard,premium");
        savePromoIfMissing("RENTCAR50", "PERCENTAGE", new BigDecimal("50"), "basic,standard,premium");
        savePromoIfMissing("INNOVAX2026", "FIXED", new BigDecimal("200"), "standard,premium");
        saveBenefitPromoIfMissing("2MONTHSFREE", "Two Months Free", "FREE_MONTHS", 2, null, null,
                "basic,standard,premium");
        saveBenefitPromoIfMissing("FREEGPS", "Free GPS Module", "FREE_FEATURE", 1, "GPS_TRACKING", null,
                "trial,basic,standard");
        saveBenefitPromoIfMissing("FREESIGN", "Free Online Signature", "FREE_FEATURE", 1, "DIGITAL_SIGNATURE", null,
                "trial,basic");
        saveBenefitPromoIfMissing("PREMIUM30", "Premium For 30 Days", "PLAN_TRIAL", 1, null, "premium",
                "trial,basic,standard");
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/gateways")
    public ResponseEntity<List<PaymentGatewayConfig>> listGateways() {
        return ResponseEntity.ok(gatewayConfigRepository.findAll());
    }

    @PostMapping("/gateways")
    public ResponseEntity<PaymentGatewayConfig> saveGateway(@RequestBody PaymentGatewayConfig config) {
        return ResponseEntity.ok(gatewayConfigRepository.save(config));
    }

    @GetMapping("/affiliate-rules")
    public ResponseEntity<List<AffiliateRule>> listAffiliateRules() {
        return ResponseEntity.ok(affiliateRuleRepository.findAll());
    }

    @PostMapping("/affiliate-rules")
    public ResponseEntity<AffiliateRule> saveAffiliateRule(@RequestBody AffiliateRule rule) {
        return ResponseEntity.ok(affiliateRuleRepository.save(rule));
    }

    @DeleteMapping("/affiliate-rules/{id}")
    public ResponseEntity<Map<String, Object>> deleteAffiliateRule(@PathVariable Long id) {
        affiliateRuleRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/affiliate-referrals")
    public ResponseEntity<List<AffiliateReferral>> listAffiliateReferrals() {
        return ResponseEntity.ok(affiliateReferralRepository.findAll());
    }

    @GetMapping("/affiliate-conversions")
    public ResponseEntity<List<AffiliateConversion>> listAffiliateConversions() {
        return ResponseEntity.ok(affiliateConversionRepository.findAll());
    }

    private void savePromoIfMissing(String code, String type, BigDecimal value, String plans) {
        if (promoCodeRepository.findByCode(code).isPresent()) return;
        promoCodeRepository.save(PromoCode.builder()
                .code(code)
                .discountType(type)
                .discountValue(value)
                .maxUses(1000)
                .usedCount(0)
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .applicablePlans(plans)
                .isActive(true)
                .build());
    }

    private void saveBenefitPromoIfMissing(
            String code,
            String name,
            String promotionType,
            Integer freeMonths,
            String freeFeatureCode,
            String trialPlanCode,
            String plans) {
        if (promoCodeRepository.findByCode(code).isPresent()) return;
        promoCodeRepository.save(PromoCode.builder()
                .code(code)
                .promotionName(name)
                .promotionType(promotionType)
                .discountType("FIXED")
                .discountValue(BigDecimal.ZERO)
                .freeMonths(freeMonths)
                .freeFeatureCode(freeFeatureCode)
                .trialPlanCode(trialPlanCode)
                .maxUses(1000)
                .usedCount(0)
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .applicablePlans(plans)
                .isActive(true)
                .build());
    }
}
