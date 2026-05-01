package com.carrental.dto.payment;

import com.carrental.entity.Payment;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Read-only payment projection.
 */
@Data
@Builder
public class PaymentResponse {

    private Long id;
    private Long reservationId;
    private BigDecimal amount;
    private boolean paid;
    private Long tenantId;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .reservationId(payment.getReservation().getId())
                .amount(payment.getAmount())
                .paid(payment.isPaid())
                .tenantId(payment.getTenant().getId())
                .build();
    }
}
