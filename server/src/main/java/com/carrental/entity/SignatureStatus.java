package com.carrental.entity;

/**
 * Lifecycle of an additional driver's independent signing workflow.
 * Plain {@code @Enumerated(STRING)}, no DB check constraint — matches how
 * {@link ContractStatus}/{@code DepositStatus} are already modeled.
 */
public enum SignatureStatus {
    NOT_REQUIRED,
    PENDING,
    LINK_SENT,
    OPENED,
    SIGNED,
    DECLINED,
    EXPIRED,
    REVOKED
}
