package com.carrental.dto.invoice;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One entry in a contract's financial audit trail (see
 * InvoiceService#getFinancialTimeline) — built only from real Contract/
 * Invoice/Payment/ContractExtension rows, never fabricated.
 */
@Data
@Builder
public class FinancialTimelineEventResponse {
    /** CONTRACT_CREATED, INVOICE_ISSUED, PAYMENT_RECEIVED, REFUND, EXTENSION, CANCELLATION. */
    private String type;
    private LocalDateTime timestamp;
    private String description;
    private BigDecimal amount;
    private String performedBy;
}
