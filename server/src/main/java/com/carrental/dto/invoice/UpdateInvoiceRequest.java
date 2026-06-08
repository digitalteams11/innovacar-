package com.carrental.dto.invoice;

import com.carrental.entity.InvoiceStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/invoices/{id}} — partial update.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateInvoiceRequest {

    private String invoiceNumber;

    private String clientName;

    private Long clientId;

    private LocalDate issueDate;

    private LocalDate dueDate;

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private InvoiceStatus status;
}
