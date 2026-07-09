package com.carrental.entity;

/**
 * Classification of payment purpose.
 * Separates rental customer payments from SaaS subscription billing.
 */
public enum PaymentType {
    RENTAL,            // Regular rental payment
    SUBSCRIPTION,      // SaaS platform subscription
    DEPOSIT,           // Legacy deposit entry
    DEPOSIT_PAYMENT,   // Deposit/guarantee collected from client
    DEPOSIT_RETURN,    // Deposit returned to client
    DAMAGE_FEE,        // Fee deducted from deposit for vehicle damage
    EXTRA_CHARGE,      // Any additional charge (cleaning, fuel, late, etc.)
    REFUND,            // General refund
    OTHER              // Miscellaneous
}
