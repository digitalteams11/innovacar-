package com.carrental.entity;

/**
 * GPS device status reported by the tracking provider.
 */
public enum GpsDeviceStatus {
    ONLINE,     // Device connected and reporting
    OFFLINE,    // Device not reporting (timeout)
    MOVING,     // Device is in motion
    STOPPED,    // Device is stationary
    IDLE,       // Engine on but not moving
    MAINTENANCE // Device temporarily disabled
}
