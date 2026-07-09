package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "inspections",
        indexes = {
                @Index(name = "idx_inspection_tenant", columnList = "tenant_id"),
                @Index(name = "idx_inspection_reservation", columnList = "reservation_id"),
                @Index(name = "idx_inspection_contract", columnList = "contract_id"),
                @Index(name = "idx_inspection_client", columnList = "client_id"),
                @Index(name = "idx_inspection_vehicle", columnList = "vehicle_id"),
                @Index(name = "idx_inspection_token", columnList = "token", unique = true),
                @Index(name = "idx_inspection_media_expires", columnList = "media_expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inspection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private User employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InspectionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InspectionStatus status;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "media_expires_at", nullable = false)
    private LocalDateTime mediaExpiresAt;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InspectionMedia> media = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = InspectionStatus.NOT_STARTED;
    }
}
