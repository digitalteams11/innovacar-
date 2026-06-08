package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Additional driver information linked to a rental contract.
 */
@Entity
@Table(name = "contract_additional_drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdditionalDriver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "cin", length = 50)
    private String cin;

    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "driver_license_number", length = 50)
    private String driverLicenseNumber;

    @Column(name = "nationality", length = 50)
    private String nationality;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    // ── Link ─────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;
}
