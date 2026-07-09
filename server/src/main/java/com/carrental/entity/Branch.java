package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_tenant_code", columnNames = {"tenant_id", "code"})
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 30)
    private String code;

    private String address;
    private String city;
    private String phone;
    private String email;

    @Column(name = "manager_name")
    private String managerName;

    @Column(name = "opening_hours")
    private String openingHours;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    /** True for the auto-created "Main Branch". Cannot be deleted. */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (active == null) active = true;
        if (isDefault == null) isDefault = false;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
