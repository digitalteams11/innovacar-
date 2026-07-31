package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "permission_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 80)
    private String category;

    /** High-level product area, e.g. "FLEET", "RESERVATIONS" — the module card this permission belongs to in the role editor. */
    @Column(length = 60)
    private String module;

    /** The entity this permission acts on, e.g. "VEHICLE" — module+resource+action together fully describe the code. */
    @Column(length = 60)
    private String resource;

    /** The verb, e.g. "CREATE"/"DELETE"/"VIEW" — drives which action column the role editor renders for this permission. */
    @Column(length = 60)
    private String action;

    /** i18n key for the display label, e.g. "permissions.vehicle.create" — no hardcoded English label in the UI. */
    @Column(name = "label_key", length = 150)
    private String labelKey;

    /** i18n key for the longer help text, e.g. "permissions.descriptions.vehicleCreate". */
    @Column(name = "description_key", length = 150)
    private String descriptionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private PermissionRiskLevel riskLevel;

    /** Comma-separated permission codes this one depends on (e.g. VEHICLE_UPDATE -> "VEHICLE_VIEW"). */
    @Column(length = 500)
    private String dependencies;

    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * False once PermissionSyncService no longer finds this code in the current
     * PermissionCatalog — the row and every role_permissions/override grant
     * referencing it are kept (never deleted), but hasResolvedPermission()
     * treats an inactive definition as an automatic deny regardless of any
     * stored grant. This is the "deactivate, never delete" contract.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * True for a legacy/alias code (e.g. VIEW_VEHICLES, superseded by
     * VEHICLE_VIEW) that must stay active for existing grants but should not
     * be offered as a "current" permission in the new role editor UI.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean deprecated = false;

    /** Only set on a deprecated legacy code — the modern canonical code it mirrors (e.g. VIEW_VEHICLES -> VEHICLE_VIEW). */
    @Column(name = "alias_of", length = 100)
    private String aliasOf;

    /** Set once, when PermissionSyncService first inserts this code — drives the "New" badge in the role editor. */
    @Column(name = "registered_at")
    private LocalDateTime registeredAt;
}
