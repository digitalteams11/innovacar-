package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A single billed item on an {@link Invoice} — rental days, an extension,
 * a return fee, a discount, etc. An invoice's {@code amount} is always the
 * sum of its lines' {@code total} (see InvoiceService#syncInvoiceForContract
 * and #createInvoice), never a figure entered independently of them.
 */
@Entity
@Table(
    name = "invoice_lines",
    indexes = @Index(name = "idx_invoice_line_invoice", columnList = "invoice_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvoiceLineType type;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
