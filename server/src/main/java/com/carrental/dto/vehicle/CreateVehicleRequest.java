package com.carrental.dto.vehicle;

import com.carrental.entity.VehicleStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/vehicles} — create a vehicle.
 */
@Data
public class CreateVehicleRequest {

    @NotBlank(message = "Marque (brand/model) is required")
    @Size(max = 150, message = "Marque must not exceed 150 characters")
    private String marque;

    @NotNull(message = "Prix par jour is required")
    @DecimalMin(value = "0.01", message = "Daily price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Prix jour format: up to 8 integer digits and 2 decimal places")
    private BigDecimal prixJour;

    /** Defaults to AVAILABLE when omitted — set explicitly for imports. */
    private VehicleStatus statut;

    /** Vehicle category e.g. Economy, SUV, Luxury */
    @Size(max = 50)
    private String category;

    /** License plate number */
    @NotBlank(message = "License plate is required")
    @Size(max = 30)
    private String plate;

    /** Fuel type: Essence, Diesel, Hybrid, Electric */
    @Size(max = 20)
    private String fuel;

    /** Transmission: Manual, Automatic */
    @Size(max = 20)
    private String transmission;

    /** Number of seats — optional, entered manually by the agency. Never defaulted. */
    @Min(value = 1, message = "Number of seats must be at least 1")
    @Max(value = 100, message = "Number of seats must not exceed 100")
    private Integer seatCount;

    /** Image URL or Base64 data URL for the vehicle */
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
}
