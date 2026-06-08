package com.carrental.entity;

/**
 * Lifecycle status of a security deposit (caution).
 */
public enum DepositStatus {
    PENDING,           // Deposit required but not yet received
    RECEIVED,          // Deposit collected from client
    HELD,              // Deposit held during active contract
    PARTIALLY_RETURNED,// Deposit returned with deductions
    RETURNED,          // Full deposit returned to client
    DEDUCTED,          // Full deposit retained by agency (damages, etc.)
    CANCELLED          // Deposit cancelled (reservation cancelled, etc.)
}
