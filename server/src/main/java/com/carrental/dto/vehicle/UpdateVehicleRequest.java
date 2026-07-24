package com.carrental.dto.vehicle;

import com.carrental.entity.VehicleStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for {@code PUT /api/vehicles/{id}} — partial update.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateVehicleRequest {

    @Size(max = 150, message = "Marque must not exceed 150 characters")
    private String marque;

    @DecimalMin(value = "0.01", message = "Daily price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Prix jour format: up to 8 integer digits and 2 decimal places")
    private BigDecimal prixJour;

    private VehicleStatus statut;

    @Size(max = 50)
    private String category;

    @Size(max = 30)
    private String plate;

    @Size(max = 20)
    private String fuel;

    @Size(max = 20)
    private String transmission;

    /** Number of seats — optional, entered manually by the agency. Never defaulted. */
    @Min(value = 1, message = "Number of seats must be at least 1")
    @Max(value = 100, message = "Number of seats must not exceed 100")
    private Integer seatCount;

    @Size(max = 50000000, message = "Image data too large")
    private String imageUrl;

    /** GPS device identifier from tracking provider */
    @Size(max = 100)
    private String gpsDeviceId;

    /** GPS device IMEI */
    @Size(max = 50)
    private String gpsImei;

    /** Enable GPS tracking for this vehicle */
    private Boolean gpsEnabled;

    /** Last known latitude */
    private Double lastLatitude;

    /** Last known longitude */
    private Double lastLongitude;

    /** Last reported speed */
    private Double lastSpeed;

    /** Current GPS device status */
    private com.carrental.entity.GpsDeviceStatus gpsStatus;
}
