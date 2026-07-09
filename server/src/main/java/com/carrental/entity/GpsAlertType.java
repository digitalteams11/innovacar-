package com.carrental.entity;

/**
 * Types of GPS alerts that can be generated.
 */
public enum GpsAlertType {
    OVERSPEED,              // Vehicle exceeded speed limit
    OFFLINE,                // Device went offline
    GEOFENCE_ENTER,         // Vehicle returned inside the allowed city zone
    GEOFENCE_EXIT,          // Vehicle exited the allowed city zone
    DEVICE_DISCONNECT,      // GPS device was disconnected
    LOW_BATTERY,            // Device battery low
    TOWING,                 // Vehicle being towed (movement without ignition)
    HARD_BRAKING,           // Sudden deceleration detected
    HARD_ACCELERATION,      // Sudden acceleration detected
    IDLING_TOO_LONG,        // Vehicle idling beyond threshold
    VEHICLE_STARTED_MOVING, // Vehicle transitioned from stopped to moving
    GPS_SYNC_ERROR          // Background sync failed for a tenant
}
