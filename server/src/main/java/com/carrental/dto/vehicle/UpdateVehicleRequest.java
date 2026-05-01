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
}
