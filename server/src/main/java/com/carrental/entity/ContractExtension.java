package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single "Prolonger la location" event on a {@link Contract} — added rental
 * days plus the amount they cost, recorded as its own row so the original
 * rental total and this addition can always be told apart (see Contract's
 * financial timeline). Never mutated after creation; the contract's own
 * {@code totalPrice}/{@code rentalDays}/{@code endDate} are updated in place
 * to the new current total at the same time this row is written (see
 * ContractService#extendContract) — this table is the audit trail for that
 * change, not a second source of truth for the contract's current state.
 */
@Entity
@Table(
    name = "contract_extensions",
    indexes = {
        @Index(name = "idx_contract_extension_contract", columnList = "contract_id"),
        @Index(name = "idx_contract_extension_tenant", columnList = "tenant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "additional_days", nullable = false)
    private Integer additionalDays;

    @Column(name = "additional_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalAmount;

    @Column(name = "previous_end_date", nullable = false)
    private LocalDate previousEndDate;

    @Column(name = "new_end_date", nullable = false)
    private LocalDate newEndDate;

    @Column(length = 500)
    private String reason;

    @Column(name = "performed_by", length = 255)
    private String performedBy;

    @Column(name = "performed_by_id")
    private Long performedById;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void ensureDefaults() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
