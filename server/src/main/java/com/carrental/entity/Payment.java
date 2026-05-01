package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a payment associated with a vehicle reservation.
 */
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_tenant", columnList = "tenant_id"),
        @Index(name = "idx_payment_reservation", columnList = "reservation_id", unique = true)
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean paid;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
