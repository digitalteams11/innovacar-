package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Represents a tenant (company / business) in the SaaS platform.
 * Every user and every piece of data belongs to exactly one tenant.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Company / business name */
    @Column(nullable = false, unique = true)
    private String name;

    /** Billing / contact e-mail — also unique per tenant */
    @Column(nullable = false, unique = true)
    private String email;

    /** Whether the subscription is currently active */
    @Column(nullable = false)
    private boolean subscriptionActive;

    /** Date on which the subscription expires */
    @Column
    private LocalDate subscriptionEndDate;

    /** Agency address */
    @Column
    private String address;

    /** Agency phone */
    @Column
    private String phone;

    /** Tax ID */
    @Column(name = "tax_id")
    private String taxId;

    /** City */
    @Column
    private String city;

    /** Country */
    @Column
    private String country;

    /**
     * Checks if the subscription is currently active and not expired.
     */
    public boolean isSubscriptionValid() {
        if (!subscriptionActive) return false;
        if (subscriptionEndDate != null && LocalDate.now().isAfter(subscriptionEndDate)) return false;
        return true;
    }
}
