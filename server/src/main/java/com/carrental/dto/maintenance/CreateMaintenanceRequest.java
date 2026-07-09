package com.carrental.dto.maintenance;

import com.carrental.entity.MaintenanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateMaintenanceRequest {
    @NotNull(message = "Vehicle is required")
    private Long vehicleId;

    @NotBlank(message = "Title is required")
    private String title;

    private String serviceProvider;

    @NotNull(message = "Scheduled date is required")
    private LocalDateTime scheduledDate;

    @PositiveOrZero(message = "Cost must be greater than or equal to 0")
    private BigDecimal cost;

    @PositiveOrZero(message = "Mileage must be greater than or equal to 0")
    private Integer mileage;

    private String description;

    private MaintenanceStatus status;
}
