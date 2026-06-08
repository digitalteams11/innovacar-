package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Security deposit (caution) transaction — single source of truth for all deposit activity.
 * Linked to Contract, Reservation, and Client for full traceability.
 */
@Entity
@Table(
    name = "deposits",
    indexes = {
        @Index(name = "idx_deposit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_deposit_contract", columnList = "contract_id"),
        @Index(name = "idx_deposit_reservation", columnList = "reservation_id"),
        @Index(name = "idx_deposit_client", columnList = "client_id"),
        @Index(name = "idx_deposit_status", columnList = "tenant_id, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Linked Entities ──────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    // ── Deposit Details ──────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_type", length = 30)
    private DepositType depositType;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    @Builder.Default
    private String currency = "MAD";

    @Column(name = "reference", length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    @Builder.Default
    private DepositStatus status = DepositStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Deposit Conditions ───────────────────────────────────────────────────

    @Column(name = "conditions_text", columnDefinition = "TEXT")
    @Builder.Default
    private String conditionsText = "The deposit will be returned after inspection of the vehicle and validation of all contractual obligations.";

    @Column(name = "conditions_accepted")
    private Boolean conditionsAccepted;

    @Column(name = "conditions_accepted_at")
    private LocalDateTime conditionsAcceptedAt;

    // ── Timeline ─────────────────────────────────────────────────────────────

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "held_at")
    private LocalDateTime heldAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    // ── Return Deductions ────────────────────────────────────────────────────

    @Column(name = "damage_deduction", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal damageDeduction = BigDecimal.ZERO;

    @Column(name = "cleaning_deduction", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cleaningDeduction = BigDecimal.ZERO;

    @Column(name = "late_fee_deduction", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal lateFeeDeduction = BigDecimal.ZERO;

    @Column(name = "fuel_deduction", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fuelDeduction = BigDecimal.ZERO;

    @Column(name = "other_deduction", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    @Column(name = "returned_amount", precision = 10, scale = 2)
    private BigDecimal returnedAmount;

    @Column(name = "return_notes", columnDefinition = "TEXT")
    private String returnNotes;

    // ── Return Inspection ────────────────────────────────────────────────────

    @Column(name = "fuel_level_end", length = 20)
    private String fuelLevelEnd;

    @Column(name = "mileage_end")
    private Integer mileageEnd;

    @Column(name = "interior_condition", length = 50)
    private String interiorCondition;

    @Column(name = "exterior_condition", length = 50)
    private String exteriorCondition;

    @Column(name = "missing_items", columnDefinition = "TEXT")
    private String missingItems;

    // ── Metadata ─────────────────────────────────────────────────────────────

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Multi-tenancy ────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ── Lifecycle callbacks ──────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = DepositStatus.PENDING;
        if (currency == null) currency = "MAD";
        if (damageDeduction == null) damageDeduction = BigDecimal.ZERO;
        if (cleaningDeduction == null) cleaningDeduction = BigDecimal.ZERO;
        if (lateFeeDeduction == null) lateFeeDeduction = BigDecimal.ZERO;
        if (fuelDeduction == null) fuelDeduction = BigDecimal.ZERO;
        if (otherDeduction == null) otherDeduction = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Computed ─────────────────────────────────────────────────────────────

    /**
     * Total deductions sum.
     */
    public BigDecimal getTotalDeductions() {
        return damageDeduction.add(cleaningDeduction)
                .add(lateFeeDeduction)
                .add(fuelDeduction)
                .add(otherDeduction);
    }

    /**
     * Amount that should be returned after deductions.
     */
    public BigDecimal getCalculatedReturnAmount() {
        if (amount == null) return BigDecimal.ZERO;
        return amount.subtract(getTotalDeductions()).max(BigDecimal.ZERO);
    }

    // ── Deposit Type Enum ────────────────────────────────────────────────────

    public enum DepositType {
        CASH,
        CHECK,
        BANK_TRANSFER,
        CARD_HOLD,
        OTHER
    }
}
