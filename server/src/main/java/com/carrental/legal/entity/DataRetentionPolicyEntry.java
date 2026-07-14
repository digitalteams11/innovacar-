package com.carrental.legal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row of the platform's data-retention schedule (e.g. "Contracts", kept
 * "10 years", legal basis "Moroccan commercial/tax record-keeping
 * obligations"). Editable by Super Admin, publicly readable so it can back
 * the Data Retention Policy document and the account-deletion/export UI.
 * Deliberately descriptive/config data only — this module does not run
 * automated purge jobs against business tables.
 */
@Entity
@Table(name = "data_retention_policy_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataRetentionPolicyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_category", nullable = false, length = 150)
    private String dataCategory;

    @Column(name = "retention_period", nullable = false, length = 150)
    private String retentionPeriod;

    @Column(name = "legal_basis", columnDefinition = "TEXT")
    private String legalBasis;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (displayOrder == null) displayOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
