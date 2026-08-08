package com.carrental.exception;

import lombok.Getter;
import java.math.BigDecimal;

/**
 * Thrown when a requested refund amount exceeds what's actually left to refund
 * on a payment (its amount minus any refund already applied) — see
 * PaymentService#refundPayment. Never silently clamp the refund to the max
 * available; the caller must see and correct the amount.
 */
@Getter
public class RefundExceedsAvailableAmountException extends RuntimeException {
    private final BigDecimal requestedAmount;
    private final BigDecimal availableAmount;

    public RefundExceedsAvailableAmountException(BigDecimal requestedAmount, BigDecimal availableAmount) {
        super("Refund amount cannot be greater than the amount still available to refund on this payment.");
        this.requestedAmount = requestedAmount;
        this.availableAmount = availableAmount;
    }
}
