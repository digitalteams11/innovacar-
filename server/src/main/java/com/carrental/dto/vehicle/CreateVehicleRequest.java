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

    /** Legacy combined field accepted for backwards compatibility; derived from brand + model for new writes. */
    @Size(max = 150, message = "Marque must not exceed 150 characters")
    private String marque;

    @NotBlank(message = "Brand is required")
    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    @NotBlank(message = "Model is required")
    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

    @NotNull(message = "Prix par jour is required")
    @DecimalMin(value = "0.01", message = "Daily price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Prix jour format: up to 8 integer digits and 2 decimal places")
    private BigDecimal prixJour;

    /** Defaults to AVAILABLE when omitted — set explicitly for imports. */
    private VehicleStatus statut;

    /** Vehicle category e.g. Economy, SUV, Luxury */
    @NotBlank(message = "Category is required")
    @Size(max = 50)
    private String category;

    /** License plate number */
    @NotBlank(message = "License plate is required")
    @Size(max = 30)
    private String plate;

    /** Fuel type: Essence, Diesel, Hybrid, Electric */
    @NotBlank(message = "Fuel is required")
    @Size(max = 20)
    private String fuel;

    /** Transmission: Manual, Automatic */
    @NotBlank(message = "Transmission is required")
    @Size(max = 20)
    private String transmission;

    /** Number of seats — optional, entered manually by the agency. Never defaulted. */
    @NotNull(message = "Number of seats is required")
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
