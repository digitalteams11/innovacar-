package com.carrental.dto.invoice;

import lombok.Data;

import java.math.BigDecimal;

/**
 * A single line item supplied when creating/editing a MANUAL invoice.
 * {@code total} is never accepted from the client — always
 * {@code quantity * unitPrice}, computed server-side (see InvoiceService).
 */
@Data
public class InvoiceLineDto {

    /** One of {@link com.carrental.entity.InvoiceLineType}. */
    private String type;

    private String description;

    private BigDecimal quantity;

    private BigDecimal unitPrice;
}
