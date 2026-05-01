package com.carrental.controller;

import com.carrental.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Subscription REST controller.
 * Located under /api/subscriptions to bypass the SubscriptionFilter,
 * ensuring admins can always renew their subscriptions even if locked out.
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // ── POST /api/subscriptions/activate ─────────────────────────────────────

    /**
     * Activates the tenant's subscription.
     * Requires ADMIN role.
     */
    @PostMapping("/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> activateSubscription() {
        subscriptionService.activateSubscription();
        return ResponseEntity.ok(Map.of("message", "Subscription activated successfully"));
    }

    // ── POST /api/subscriptions/extend ───────────────────────────────────────

    /**
     * Extends the tenant's subscription.
     * Requires ADMIN role.
     */
    @PostMapping("/extend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> extendSubscription(@RequestParam int days) {
        subscriptionService.extendSubscription(days);
        return ResponseEntity.ok(Map.of("message", "Subscription extended successfully by " + days + " days"));
    }
}
