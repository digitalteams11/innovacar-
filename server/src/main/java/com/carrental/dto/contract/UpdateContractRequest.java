package com.carrental.dto.contract;

import com.carrental.entity.ContractStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/contracts/{id}} — partial update.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateContractRequest {

    private String contractNumber;

    private String clientName;

    private String vehicleMarque;

    private LocalDate startDate;

    private LocalDate endDate;

    private ContractStatus status;

    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
    private BigDecimal totalAmount;
}
