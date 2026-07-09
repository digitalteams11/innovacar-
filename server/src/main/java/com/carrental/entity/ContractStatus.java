package com.carrental.entity;

/**
 * Complete lifecycle states of a rental contract.
 */
public enum ContractStatus {
    DRAFT,
    WAITING_SIGNATURE,
    WAITING_CLIENT_SIGNATURE,
    // Legacy values retained for existing rows.
    PENDING_SIGNATURE,
    PARTIALLY_SIGNED,
    SIGNED,
    ACTIVE,
    PAID,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
