package com.carrental.dto.gps;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A GPS trip segment (from start moving to stop).
 */
@Data
@Builder
public class GpsTripResponse {

    private Long vehicleId;
    private String vehicleName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double startLatitude;
    private Double startLongitude;
    private Double endLatitude;
    private Double endLongitude;
    private Double distanceKm;
    private Double maxSpeed;
    private Double avgSpeed;
    private Long durationMinutes;
    private String startAddress;
    private String endAddress;
}
