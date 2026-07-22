package com.carrental.controller;

import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Whop webhook receiver.
 *
 * POST /api/webhooks/whop
 *
 * Verifies HMAC-SHA256 signature from Whop, then:
 *   - membership.went_valid / payment.succeeded → activates subscription
 *   - membership.went_invalid / payment.failed   → suspends subscription
 *   - membership.deleted / membership.expired     → marks as cancelled/expired
 *
 * Idempotent: each unique Whop event ID is recorded in subscription_events
 * before processing; duplicate deliveries are silently acknowledged.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WhopWebhookController {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final SubscriptionService subscriptionService;

    @Value("${whop.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/whop")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleWhopWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "whop-signature", required = false) String whopSignature,
            @RequestHeader(value = "x-whop-signature", required = false) String xWhopSignature) {

        String signature = whopSignature != null ? whopSignature : xWhopSignature;

        // Verify signature if secret is configured
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!verifySignature(rawBody, signature)) {
                log.warn("[WHOP_WEBHOOK] Invalid signature — rejecting event");
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Invalid webhook signature"));
            }
        } else {
            log.warn("[WHOP_WEBHOOK] No webhook secret configured — skipping signature verification (set WHOP_WEBHOOK_SECRET in production)");
        }

        // Parse event type and data from raw JSON (minimal parsing)
        String eventType   = extractJsonString(rawBody, "event");
        String tenantIdStr = extractNestedJsonString(rawBody, "metadata", "tenant_id");
        String promoCodeStr = extractNestedJsonString(rawBody, "metadata", "promo_code");
        String membershipId = extractJsonString(rawBody, "id");
        String planId      = extractJsonString(rawBody, "plan_id");

        // Whop sends a unique event id; use it for idempotency.
        // The "id" field on the root object is the event/membership id.
        String whopEventId = membershipId != null ? eventType + ":" + membershipId : null;

        log.info("[WHOP_WEBHOOK] event={} tenantId={} membershipId={}", eventType, tenantIdStr, membershipId);

        if (eventType == null || eventType.isBlank()) {
            log.warn("[WHOP_WEBHOOK] Missing event type in payload");
            return ResponseEntity.ok(Map.of("success", true, "message", "Event ignored — no event type"));
        }

        // Idempotency: skip already-processed events
        if (whopEventId != null && subscriptionEventRepository.existsByWhopEventId(whopEventId)) {
            log.info("[WHOP_WEBHOOK] Duplicate event skipped: {}", whopEventId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Event already processed"));
        }

        Long tenantId = parseLong(tenantIdStr);
        if (tenantId == null) {
            log.warn("[WHOP_WEBHOOK] Could not resolve tenantId from metadata for event={}", eventType);
            return ResponseEntity.ok(Map.of("success", true, "message", "Event received — tenant not identified"));
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            log.warn("[WHOP_WEBHOOK] Tenant {} not found for event={}", tenantId, eventType);
            return ResponseEntity.ok(Map.of("success", true, "message", "Event received — tenant not found"));
        }

        // Record event before processing so re-delivery is safely skipped
        if (whopEventId != null) {
            subscriptionEventRepository.save(SubscriptionEvent.builder()
                    .whopEventId(whopEventId)
                    .eventType(eventType)
                    .tenantId(tenantId)
                    .membershipId(membershipId)
                    .build());
        }

        return switch (eventType) {
            case "membership.went_valid", "payment.succeeded", "membership.created" ->
                    handleActivation(tenant, planId, membershipId, eventType, promoCodeStr);

            case "membership.went_invalid", "payment.failed", "membership.payment_failed" ->
                    handlePaymentFailed(tenant, eventType);

            case "membership.deleted", "membership.expired", "subscription.cancelled" ->
                    handleCancellation(tenant, eventType);

            default -> {
                log.info("[WHOP_WEBHOOK] Unhandled event type: {}", eventType);
                yield ResponseEntity.ok(Map.of("success", true, "message", "Event received but not handled: " + eventType));
            }
        };
    }

    // ── Event handlers ────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> handleActivation(
            Tenant tenant, String whopPlanId, String membershipId, String eventType, String promoCode) {

        // Resolve plan by whopPlanId or whopProductId
        SubscriptionPlan plan = null;
        if (whopPlanId != null && !whopPlanId.isBlank()) {
            plan = planRepository.findAll().stream()
                    .filter(p -> whopPlanId.equals(p.getWhopPlanId())
                            || whopPlanId.equals(p.getWhopPriceId())
                            || whopPlanId.equals(p.getWhopProductId()))
                    .findFirst().orElse(null);
        }
        if (plan == null && tenant.getPlanName() != null) {
            String tenantPlanName = tenant.getPlanName();
            plan = planRepository.findByName(tenantPlanName)
                    .or(() -> planRepository.findByCode(tenantPlanName))
                    .orElse(null);
        }

        int months = 1;
        if (plan != null) {
            tenant = subscriptionService.activatePaidPlan(tenant, plan, months);
        } else {
            // No plan resolved — extend subscription by 1 month
            subscriptionService.extendSubscription(30);
        }

        Long activatedTenantId = tenant.getId();
        log.info("[WHOP_WEBHOOK] ACTIVATED tenantId={} plan={} event={} membershipId={}",
                activatedTenantId, plan != null ? plan.getCode() : "UNKNOWN", eventType, membershipId);

        // Redeem the promo reservation (RESERVED → USED) only now that a real
        // provider event confirms the checkout actually went through — never
        // at the moment the checkout link was merely created (see BillingController).
        if (promoCode != null && !promoCode.isBlank()) {
            promoCodeRedemptionRepository
                    .findFirstByPromoCode_CodeIgnoreCaseAndTenantIdAndStatusOrderByRedeemedAtDesc(
                            promoCode.trim(), activatedTenantId, "RESERVED")
                    .ifPresent(redemption -> {
                        redemption.setStatus("USED");
                        promoCodeRedemptionRepository.save(redemption);
                        log.info("[WHOP_WEBHOOK] Promo redemption confirmed: promo={} tenantId={} redemptionId={}",
                                promoCode, activatedTenantId, redemption.getId());
                    });
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscription activated for tenant " + tenant.getId()));
    }

    private ResponseEntity<Map<String, Object>> handlePaymentFailed(Tenant tenant, String eventType) {
        tenant.setStatus("PAST_DUE");
        tenantRepository.save(tenant);

        log.info("[WHOP_WEBHOOK] PAYMENT_FAILED tenantId={} event={}", tenant.getId(), eventType);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscription marked as past due for tenant " + tenant.getId()));
    }

    private ResponseEntity<Map<String, Object>> handleCancellation(Tenant tenant, String eventType) {
        tenant.setStatus("CANCELLED");
        tenant.setSubscriptionActive(false);
        tenantRepository.save(tenant);

        log.info("[WHOP_WEBHOOK] CANCELLED tenantId={} event={}", tenant.getId(), eventType);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscription cancelled for tenant " + tenant.getId()));
    }

    // ── Signature verification ────────────────────────────────────────────────────

    private boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            String expected = "sha256=" + hex;
            // Compare without early return to resist timing attacks
            return constantTimeEquals(expected, signature) || constantTimeEquals(hex.toString(), signature);
        } catch (Exception e) {
            log.error("[WHOP_WEBHOOK] Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // ── JSON parsing helpers ──────────────────────────────────────────────────────

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    /** Extracts a string value from a nested object in JSON. */
    private String extractNestedJsonString(String json, String parentKey, String childKey) {
        String parentSearch = "\"" + parentKey + "\":{";
        int parentStart = json.indexOf(parentSearch);
        if (parentStart < 0) return null;
        int blockStart = parentStart + parentSearch.length();
        int blockEnd = json.indexOf("}", blockStart);
        if (blockEnd < 0) return null;
        String block = json.substring(blockStart, blockEnd);
        return extractJsonString("{" + block + "}", childKey);
    }

    private Long parseLong(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
