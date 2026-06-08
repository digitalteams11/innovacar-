package com.carrental.entity;

/**
 * Classification of payment purpose.
 * Separates rental customer payments from SaaS subscription billing.
 */
public enum PaymentType {
    RENTAL,
    SUBSCRIPTION,
    DEPOSIT,
    REFUND,
    OTHER
}
