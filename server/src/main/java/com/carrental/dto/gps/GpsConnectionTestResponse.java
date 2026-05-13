package com.carrental.dto.gps;

import lombok.Builder;
import lombok.Data;

/**
 * Response from a GPS provider connection test.
 */
@Data
@Builder
public class GpsConnectionTestResponse {

    private boolean success;
    private String message;
    private String provider;
    private Integer devicesFound;
    private String responseTime;
    private String errorCode;
}
