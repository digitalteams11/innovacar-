package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a vehicle reservation within a tenant's fleet.
 */
@Entity
@Table(
    name = "reservations",
    indexes = {
        @Index(name = "idx_reservation_tenant", columnList = "tenant_id"),
        @Index(name = "idx_reservation_vehicle", columnList = "vehicle_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "date_start", nullable = false)
    private LocalDate dateStart;

    @Column(name = "date_end", nullable = false)
    private LocalDate dateEnd;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
