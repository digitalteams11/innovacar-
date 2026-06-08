package com.carrental.entity;

/**
 * Lifecycle states of a payment transaction.
 */
public enum PaymentStatus {
    PENDING,
    PARTIALLY_PAID,
    PAID,
    REFUNDED,
    CANCELLED,
    FAILED,
    EXPIRED
}
