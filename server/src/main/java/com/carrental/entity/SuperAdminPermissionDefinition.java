package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

/** Catalog of platform-level permission codes (AGENCIES_VIEW, PAYMENTS_UPDATE, etc). */
@Entity
@Table(name = "super_admin_permission_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminPermissionDefinition {

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
}
