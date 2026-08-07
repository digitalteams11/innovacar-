package com.carrental.dto.vehicle;

import com.carrental.entity.VehicleStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/vehicles/{id}} — partial update.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateVehicleRequest {

    @Size(max = 150, message = "Marque must not exceed 150 characters")
    private String marque;

    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

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

    // ── Document checklist — see CreateVehicleRequest for semantics. Any
    // field left null is ignored (existing value kept), same as every other
    // field on this partial-update DTO. To explicitly clear a date, the
    // vehicle form must not treat "no change" and "clear" as the same thing
    // — out of scope for this endpoint's existing null-means-unchanged
    // contract, consistent with every other optional field here. ──────────

    private LocalDate licenseExpiryDate;
    private LocalDate insuranceExpiration;
    private LocalDate vignetteExpiration;
    private LocalDate technicalInspectionExpiration;
    private LocalDate circulationAuthorizationExpiryDate;
}
