package com.carrental.dto.gps;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A single GPS position point.
 */
@Data
@Builder
public class GpsPositionResponse {

    private Long vehicleId;
    private String vehicleName;
    private String deviceId;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double heading;
    private Double altitude;
    private String address;
    private LocalDateTime timestamp;
    private String status;
}
