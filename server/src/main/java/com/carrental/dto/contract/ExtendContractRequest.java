package com.carrental.dto.contract;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code POST /api/contracts/{id}/extend} — "Prolonger la
 * location". The additional amount is always computed server-side from
 * {@code Contract.dailyPrice}, never accepted from the client.
 */
@Data
public class ExtendContractRequest {

    @NotNull(message = "Number of additional days is required")
    @Min(value = 1, message = "Additional days must be at least 1")
    private Integer additionalDays;

    private String reason;
}
