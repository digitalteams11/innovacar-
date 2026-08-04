package com.carrental.entity;

/**
 * Payment states of an invoice.
 */
public enum InvoiceStatus {
    PAID,
    PENDING,
    OVERDUE,

    // ── Added for backend-generated invoice PDFs (widened additively — existing
    // PAID/PENDING/OVERDUE values and all logic built on them are unchanged) ──
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    CANCELLED,
    REFUNDED
}
