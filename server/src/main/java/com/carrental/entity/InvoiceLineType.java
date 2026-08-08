package com.carrental.entity;

/**
 * What an {@link InvoiceLine} represents on the invoice.
 */
public enum InvoiceLineType {
    RENTAL,
    EXTENSION,
    EXTRA_KM,
    FUEL,
    LATE_RETURN,
    DAMAGE,
    DELIVERY,
    CLEANING,
    DISCOUNT,
    OTHER
}
