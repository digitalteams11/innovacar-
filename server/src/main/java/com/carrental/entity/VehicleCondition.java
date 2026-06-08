package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Vehicle inspection condition recorded at pickup and return.
 */
@Entity
@Table(name = "contract_vehicle_conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Damage flags ─────────────────────────────────────────────────────────

    @Column(name = "front_damage")
    private Boolean frontDamage = false;

    @Column(name = "rear_damage")
    private Boolean rearDamage = false;

    @Column(name = "left_side_damage")
    private Boolean leftSideDamage = false;

    @Column(name = "right_side_damage")
    private Boolean rightSideDamage = false;

    @Column(name = "windshield_damage")
    private Boolean windshieldDamage = false;

    @Column(name = "interior_damage")
    private Boolean interiorDamage = false;

    @Column(name = "roof_damage")
    private Boolean roofDamage = false;

    @Column(name = "bumper_front_damage")
    private Boolean bumperFrontDamage = false;

    @Column(name = "bumper_rear_damage")
    private Boolean bumperRearDamage = false;

    @Column(name = "hood_damage")
    private Boolean hoodDamage = false;

    @Column(name = "trunk_damage")
    private Boolean trunkDamage = false;

    // ── Condition details ────────────────────────────────────────────────────

    @Column(name = "tire_condition", length = 50)
    private String tireCondition;

    @Column(name = "scratch_description", columnDefinition = "TEXT")
    private String scratchDescription;

    @Column(name = "dent_description", columnDefinition = "TEXT")
    private String dentDescription;

    @Column(name = "general_notes", columnDefinition = "TEXT")
    private String generalNotes;

    @Column(name = "condition_photos", columnDefinition = "TEXT")
    private String conditionPhotos;

    @Column(name = "inspection_date")
    private java.time.LocalDateTime inspectionDate;

    @Column(name = "inspected_by", length = 150)
    private String inspectedBy;

    @Column(name = "is_pickup_inspection")
    private Boolean isPickupInspection = true;

    // ── Link ─────────────────────────────────────────────────────────────────

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;
}
