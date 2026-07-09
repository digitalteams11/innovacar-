package com.carrental.entity;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    CONVERTED_TO_CONTRACT,
    // Legacy values retained for existing rows.
    ACTIVE,
    COMPLETED,
    EXPIRED
}
