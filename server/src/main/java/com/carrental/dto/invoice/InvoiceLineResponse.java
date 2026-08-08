package com.carrental.dto.invoice;

import com.carrental.entity.InvoiceLine;
import com.carrental.entity.InvoiceLineType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InvoiceLineResponse {
    private Long id;
    private InvoiceLineType type;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal total;

    public static InvoiceLineResponse from(InvoiceLine line) {
        return InvoiceLineResponse.builder()
                .id(line.getId())
                .type(line.getType())
                .description(line.getDescription())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .total(line.getTotal())
                .build();
    }
}
