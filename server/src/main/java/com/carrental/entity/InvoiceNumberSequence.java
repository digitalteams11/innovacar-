package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-tenant, per-year counter backing sequential invoice numbers
 * (FAC-2026-000001). Incremented under a pessimistic row lock in
 * {@code NumberGeneratorService#generateInvoiceNumber} so concurrent invoice
 * creation can never produce the same number twice.
 */
@Entity
@Table(name = "invoice_number_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(InvoiceNumberSequence.Key.class)
public class InvoiceNumberSequence {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Id
    @Column(name = "year")
    private Integer year;

    @Column(name = "last_number", nullable = false)
    private Long lastNumber;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements java.io.Serializable {
        private Long tenantId;
        private Integer year;
    }
}
