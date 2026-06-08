package com.carrental.dto.deposit;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepositReturnRequest {

    @NotNull(message = "Damage deduction is required")
    private BigDecimal damageDeduction;

    @NotNull(message = "Cleaning deduction is required")
    private BigDecimal cleaningDeduction;

    @NotNull(message = "Late fee deduction is required")
    private BigDecimal lateFeeDeduction;

    @NotNull(message = "Fuel deduction is required")
    private BigDecimal fuelDeduction;

    @NotNull(message = "Other deduction is required")
    private BigDecimal otherDeduction;

    private String returnNotes;

    private String fuelLevelEnd;
    private Integer mileageEnd;
    private String interiorCondition;
    private String exteriorCondition;
    private String missingItems;
}
