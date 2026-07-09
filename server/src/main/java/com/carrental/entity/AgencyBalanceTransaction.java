package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only ledger of every manual balance change a Super Admin makes for
 * an agency (credit, debit, manual payment record, refund, adjustment).
 * Never updated/deleted — the running {@link Tenant#getBalance()} is the
 * only mutable field; this table is the audit trail behind it.
 */
@Entity
@Table(name = "agency_balance_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyBalanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Balance after this transaction was applied — captured for a tamper-evident running total. */
    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 200)
    private String reference;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Type { CREDIT, DEBIT, PAYMENT, REFUND, ADJUSTMENT }
}
