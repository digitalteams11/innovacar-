package com.carrental.dto.gps;

import com.carrental.entity.GpsDeviceStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to update GPS configuration for a vehicle.
 */
@Data
public class UpdateGpsRequest {

    @Size(max = 100)
    private String gpsDeviceId;

    @Size(max = 50)
    private String gpsImei;

    private Boolean gpsEnabled;

    private GpsDeviceStatus gpsStatus;

    private Double lastLatitude;

    private Double lastLongitude;

    private Double lastSpeed;
}
