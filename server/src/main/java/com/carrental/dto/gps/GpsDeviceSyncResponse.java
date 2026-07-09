package com.carrental.dto.gps;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response from GPS device synchronization.
 */
@Data
@Builder
public class GpsDeviceSyncResponse {

    private boolean success;
    private String message;
    private int devicesSynced;
    private int devicesCreated;
    private int devicesUpdated;
    private int matchedVehicles;
    private int unmatchedDevices;
    private List<GpsDeviceInfo> devices;

    @Data
    @Builder
    public static class GpsDeviceInfo {
        private String deviceId;
        private String name;
        private String imei;
        private String plateNumber;
        private String status;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Long   linkedVehicleId;
    }
}
