package com.carrental.dto.gps;

import com.carrental.entity.GpsAlert;
import com.carrental.entity.GpsAlertType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * GPS alert projection.
 */
@Data
@Builder
public class GpsAlertResponse {

    private Long id;
    private GpsAlertType alertType;
    private String message;
    private String severity;
    private Boolean read;
    private Long vehicleId;
    private String vehicleName;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private LocalDateTime createdAt;

    public static GpsAlertResponse from(GpsAlert alert) {
        return GpsAlertResponse.builder()
                .id(alert.getId())
                .alertType(alert.getAlertType())
                .message(alert.getMessage())
                .severity(alert.getSeverity())
                .read(alert.getRead())
                .vehicleId(alert.getVehicleId())
                .vehicleName(alert.getVehicleName())
                .latitude(alert.getLatitude())
                .longitude(alert.getLongitude())
                .speed(alert.getSpeed())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
