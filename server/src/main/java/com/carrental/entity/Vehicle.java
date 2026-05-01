package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a single vehicle in a tenant's fleet.
 *
 * <p>Multi-tenancy: every vehicle is bound to exactly one {@link Tenant}.
 * The {@code tenant_id} column is the discriminator used by all repository
 * queries to enforce row-level tenant isolation.
 */
@Entity
@Table(
    name = "vehicles",
    indexes = {
        @Index(name = "idx_vehicle_tenant",        columnList = "tenant_id"),
        @Index(name = "idx_vehicle_tenant_statut",  columnList = "tenant_id, statut")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Brand and/or model name, e.g. "Toyota Corolla 2023" */
    @Column(nullable = false, length = 150)
    private String marque;

    /** Daily rental price (stored as NUMERIC(10,2) in PostgreSQL) */
    @Column(name = "prix_jour", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixJour;

    /** Current lifecycle state of the vehicle */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus statut;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
