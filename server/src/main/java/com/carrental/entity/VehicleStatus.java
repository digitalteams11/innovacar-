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
    RENTED,
    MAINTENANCE
}
