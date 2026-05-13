package com.carrental.entity;

/**
 * Types of GPS alerts that can be generated.
 */
public enum GpsAlertType {
    OVERSPEED,          // Vehicle exceeded speed limit
    OFFLINE,            // Device went offline
    GEOFENCE_ENTER,     // Vehicle entered a geofence zone
    GEOFENCE_EXIT,      // Vehicle exited a geofence zone
    DEVICE_DISCONNECT,  // GPS device was disconnected
    LOW_BATTERY,        // Device battery low
    TOWING,             // Vehicle being towed (movement without ignition)
    HARD_BRAKING,       // Sudden deceleration detected
    HARD_ACCELERATION,  // Sudden acceleration detected
    IDLING_TOO_LONG     // Vehicle idling beyond threshold
}
