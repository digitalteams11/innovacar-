package com.carrental.dto.reservation;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for {@code POST /api/reservations}.
 */
@Data
public class CreateReservationRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    private Long clientId;

    private String pickupLocation;

    private String returnLocation;

    private String notes;

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must not be in the past")
    private LocalDate dateStart;

    private LocalTime startTime;

    @NotNull(message = "End date is required")
    @FutureOrPresent(message = "End date must not be in the past")
    private LocalDate dateEnd;

    private LocalTime endTime;

    // ── Security Deposit ─────────────────────────────────────────────────────

    private Boolean depositRequired;
    private String depositType;
    private BigDecimal depositAmount;
    private String depositReference;
    private String depositNotes;
}
