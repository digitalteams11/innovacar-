package com.carrental.dto.payment;

import com.carrental.entity.PaymentMethod;
import com.carrental.entity.PaymentType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for recording a new payment.
 */
@Data
public class RecordPaymentRequest {

    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String reference;
    private PaymentType type;
    private Long reservationId;
    private Long contractId;
    private Long invoiceId;
    private Long clientId;
    private Long vehicleId;
    private String notes;

    /** Optional. When supplied and a payment already exists under this key for the
     *  current tenant, the existing payment is returned instead of a duplicate being
     *  recorded — see PaymentService#recordPayment. */
    private String idempotencyKey;
}
