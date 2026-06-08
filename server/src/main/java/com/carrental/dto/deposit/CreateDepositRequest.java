package com.carrental.dto.deposit;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDepositRequest {

    private Long reservationId;
    private Long contractId;
    private Long clientId;

    @NotNull(message = "Deposit type is required")
    private String depositType;

    @NotNull(message = "Deposit amount is required")
    private BigDecimal amount;

    private String currency = "MAD";
    private String reference;
    private String notes;
    private String conditionsText;
}
