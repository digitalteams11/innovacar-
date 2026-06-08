package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a client (customer) with full profile for relational auto-fill.
 */
@Entity
@Table(
    name = "clients",
    indexes = {
        @Index(name = "idx_client_tenant", columnList = "tenant_id"),
        @Index(name = "idx_client_phone", columnList = "tenant_id, phone"),
        @Index(name = "idx_client_email", columnList = "tenant_id, email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(name = "secondary_phone", length = 50)
    private String secondaryPhone;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 50)
    private String nationality;

    @Column(length = 10)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "cin", length = 50)
    private String cin;

    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "driving_license", length = 100)
    private String drivingLicense;

    @Column(name = "driving_license_issue")
    private LocalDate drivingLicenseIssue;

    @Column(name = "driving_license_expiry")
    private LocalDate drivingLicenseExpiry;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Relations ────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Contract> contracts = new ArrayList<>();

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
