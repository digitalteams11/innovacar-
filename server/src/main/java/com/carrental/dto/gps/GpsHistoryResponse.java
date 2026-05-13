package com.carrental.dto.gps;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Historical GPS route point for route playback.
 */
@Data
@Builder
public class GpsHistoryResponse {

    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double heading;
    private LocalDateTime timestamp;
    private String status;
    private String address;
}
