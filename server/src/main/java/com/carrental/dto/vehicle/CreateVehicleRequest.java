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
}
