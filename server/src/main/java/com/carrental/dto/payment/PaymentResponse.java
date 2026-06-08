package com.carrental.dto.payment;

import com.carrental.entity.Payment;
import com.carrental.entity.PaymentMethod;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full read-only payment projection with linked entity names.
 */
@Data
@Builder
public class PaymentResponse {

    private Long id;
    private String paymentNumber;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private PaymentMethod paymentMethod;
    private String reference;
    private PaymentStatus status;
    private PaymentType type;

    // Linked entities
    private Long reservationId;
    private String reservationLabel;
    private Long contractId;
    private String contractNumber;
    private Long invoiceId;
    private String invoiceNumber;
    private Long clientId;
    private String clientName;
    private Long vehicleId;
    private String vehicleLabel;

    private String notes;
    private Long tenantId;
    private LocalDateTime createdAt;

    /** Convenience check — true when status is PAID. */
    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentMethod(payment.getPaymentMethod())
                .reference(payment.getReference())
                .status(payment.getStatus())
                .type(payment.getType())
                .reservationId(payment.getReservation() != null ? payment.getReservation().getId() : null)
                .reservationLabel(payment.getReservation() != null
                        ? "RES-" + payment.getReservation().getId()
                        : null)
                .contractId(payment.getContract() != null ? payment.getContract().getId() : null)
                .contractNumber(payment.getContract() != null ? payment.getContract().getContractNumber() : null)
                .invoiceId(payment.getInvoice() != null ? payment.getInvoice().getId() : null)
                .invoiceNumber(payment.getInvoice() != null ? payment.getInvoice().getInvoiceNumber() : null)
                .clientId(payment.getClient() != null ? payment.getClient().getId() : null)
                .clientName(payment.getClient() != null ? payment.getClient().getName() : null)
                .vehicleId(payment.getVehicle() != null ? payment.getVehicle().getId() : null)
                .vehicleLabel(payment.getVehicle() != null ? payment.getVehicle().getMarque() : null)
                .notes(payment.getNotes())
                .tenantId(payment.getTenant() != null ? payment.getTenant().getId() : null)
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
