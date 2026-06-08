package com.carrental.dto.deposit;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateDepositRequest {

    private String depositType;
    private BigDecimal amount;
    private String reference;
    private String notes;
    private String conditionsText;
    private String status;
}
