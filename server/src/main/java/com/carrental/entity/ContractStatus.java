package com.carrental.entity;

/**
 * Complete lifecycle states of a rental contract.
 */
public enum ContractStatus {
    DRAFT,
    PENDING_SIGNATURE,
    PARTIALLY_SIGNED,
    SIGNED,
    ACTIVE,
    PAID,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
