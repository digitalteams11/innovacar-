package com.carrental.entity;

/**
 * Lifecycle state of a vehicle within a tenant's fleet.
 *
 *  AVAILABLE – ready to be rented
 *  RENTED    – currently on an active rental contract
 *  MAINTENANCE – temporarily out of service (bonus state, easily extensible)
 */
public enum VehicleStatus {
    AVAILABLE,
    RESERVED,
    RENTED,
    IN_MAINTENANCE,
    OUT_OF_SERVICE,
    /**
     * Legacy value retained for existing rows. New writes should use IN_MAINTENANCE.
     */
    MAINTENANCE
}
