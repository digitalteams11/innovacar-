package com.carrental.exception;

import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class PaidAmountExceedsTotalException extends RuntimeException {
    private final BigDecimal paidAmount;
    private final BigDecimal totalAmount;

    public PaidAmountExceedsTotalException(BigDecimal paidAmount, BigDecimal totalAmount) {
        super("Amount paid cannot be greater than contract total.");
        this.paidAmount = paidAmount;
        this.totalAmount = totalAmount;
    }
}
