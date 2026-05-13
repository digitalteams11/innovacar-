package com.carrental.dto.contract;

import com.carrental.entity.ContractStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/contracts} — create a contract.
 */
@Data
public class CreateContractRequest {

    @NotBlank(message = "Contract number is required")
    private String contractNumber;

    @NotBlank(message = "Client name is required")
    private String clientName;

    @NotBlank(message = "Vehicle marque is required")
    private String vehicleMarque;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private ContractStatus status;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
    private BigDecimal totalAmount;
}
