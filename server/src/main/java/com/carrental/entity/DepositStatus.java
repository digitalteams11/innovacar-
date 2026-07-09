package com.carrental.entity;

/**
 * Lifecycle status of a security deposit (caution).
 */
public enum DepositStatus {
    NOT_REQUIRED,      // No deposit required for this contract
    PENDING,           // Deposit required but not yet received
    RECEIVED,          // Deposit collected from client
    HELD,              // Deposit held during active contract
    PARTIALLY_RETURNED,// Deposit returned with deductions
    RETURNED,          // Full deposit returned to client
    KEPT,              // Full deposit kept by agency (client forfeited it)
    DEDUCTED,          // Full deposit retained by agency (damages, etc.)
    CANCELLED          // Deposit cancelled (reservation cancelled, etc.)
}
