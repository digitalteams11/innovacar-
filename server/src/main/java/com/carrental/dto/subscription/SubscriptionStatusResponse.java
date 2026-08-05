package com.carrental.dto.subscription;

import com.carrental.entity.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Normalized subscription-status shape (spec §25). Merged as ADDITIONAL keys
 * into {@code SubscriptionController#getSubscriptionStatus}'s existing
 * {@code Map<String,Object>} response — the older, broader map is kept as-is
 * since other frontend code may already depend on its extra fields.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponse {
    private String planCode;
    private SubscriptionStatus status;
    private LocalDateTime trialEndsAt;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime gracePeriodEnd;
    private Long daysRemaining;
    /** FULL | READ_ONLY | BLOCKED — READ_ONLY is reserved, not emitted today (no partial-read tier in the current filter). */
    private String accessMode;
    private boolean canCheckout;
}
