package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a financial transaction in the platform.
 * Serves as the single source of truth for all payments — rental, subscription, deposit, and refund.
 */
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_tenant", columnList = "tenant_id"),
        @Index(name = "idx_payment_status", columnList = "tenant_id, status"),
        @Index(name = "idx_payment_type", columnList = "tenant_id, type"),
        @Index(name = "idx_payment_client", columnList = "client_id"),
        @Index(name = "idx_payment_contract", columnList = "contract_id"),
        @Index(name = "idx_payment_invoice", columnList = "invoice_id"),
        @Index(name = "idx_payment_reservation", columnList = "reservation_id"),
        @Index(name = "idx_payment_number", columnList = "payment_number", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_number", nullable = false, unique = true, length = 50)
    private String paymentNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Builder.Default
    @Column(name = "paid", nullable = false)
    private Boolean paid = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentType type;

    // ── Relational links (all nullable because a payment may link to different entities) ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ── Audit timestamps ────────────────────────────────────────────────────

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Convenience check - true when this payment has been fully settled. */
    public boolean isPaid() {
        return Boolean.TRUE.equals(paid) || status == PaymentStatus.PAID;
    }

    @PrePersist
    @PreUpdate
    protected void ensureDefaults() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) status = PaymentStatus.PENDING;
        if (type == null) type = PaymentType.RENTAL;
        if (paymentDate == null) paymentDate = LocalDateTime.now();
        if (paid == null || status == PaymentStatus.PAID) {
            paid = status == PaymentStatus.PAID;
        }
        updatedAt = LocalDateTime.now();
    }
}
