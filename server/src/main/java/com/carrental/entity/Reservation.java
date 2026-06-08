package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a vehicle reservation with full relational links.
 */
@Entity
@Table(
    name = "reservations",
    indexes = {
        @Index(name = "idx_reservation_tenant", columnList = "tenant_id"),
        @Index(name = "idx_reservation_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_reservation_client", columnList = "client_id"),
        @Index(name = "idx_reservation_status", columnList = "tenant_id, status")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Column(name = "date_start", nullable = false)
    private LocalDate dateStart;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "date_end", nullable = false)
    private LocalDate dateEnd;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "pickup_location", length = 255)
    private String pickupLocation;

    @Column(name = "return_location", length = 255)
    private String returnLocation;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReservationStatus status;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @PrePersist
    protected void onCreate() {
        if (startTime == null) startTime = LocalTime.of(9, 0);
        if (endTime == null) endTime = LocalTime.of(18, 0);
        if (status == null) status = ReservationStatus.PENDING;
        if (paymentStatus == null) paymentStatus = "PENDING";
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;
    }
}
