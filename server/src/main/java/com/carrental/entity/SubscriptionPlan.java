package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Defines a SaaS subscription plan available on the platform.
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plan name: Trial, Basic, Standard, Premium, Enterprise */
    @Column(nullable = false, unique = true)
    private String name;

    /** Plan code for internal reference */
    @Column(nullable = false, unique = true)
    private String code;

    /** Monthly price */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    /** Yearly price (discounted) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal yearlyPrice;

    /** Description of the plan */
    @Column(length = 1000)
    private String description;

    /** Max vehicles allowed */
    @Column(name = "max_vehicles")
    private Integer maxVehicles;

    /** Max employees allowed */
    @Column(name = "max_employees")
    private Integer maxEmployees;

    /** Max GPS devices allowed */
    @Column(name = "max_gps_devices")
    private Integer maxGpsDevices;

    /** Max reservations per month */
    @Column(name = "max_reservations")
    private Integer maxReservations;

    /** Storage limit in MB */
    @Column(name = "storage_limit_mb")
    private Integer storageLimitMb;

    /** Whether this plan includes API access */
    @Column(name = "api_access")
    private Boolean apiAccess;

    /** Whether this plan includes white-label option */
    @Column(name = "white_label")
    private Boolean whiteLabel;

    /** Whether this plan includes priority support */
    @Column(name = "priority_support")
    private Boolean prioritySupport;

    /** Whether this plan is active and available for purchase */
    @Column(name = "is_active")
    private Boolean isActive;

    /** Plan features as JSON string */
    @Column(name = "features_json", length = 2000)
    private String featuresJson;

    /** Display order */
    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (apiAccess == null) apiAccess = false;
        if (whiteLabel == null) whiteLabel = false;
        if (prioritySupport == null) prioritySupport = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
