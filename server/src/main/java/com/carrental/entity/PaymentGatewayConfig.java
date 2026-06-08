package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_gateway_configs", indexes = {
        @Index(name = "idx_gateway_provider", columnList = "provider", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentGatewayConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String provider; // CMI_MOROCCO, STRIPE, PAYPAL, WHOP

    @Column(nullable = false)
    private Boolean enabled;

    @Column(length = 30)
    private String mode; // TEST, LIVE

    @Column(length = 1000)
    private String publicConfigJson;

    @Column(length = 1000)
    private String privateConfigRef;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = false;
        if (mode == null) mode = "TEST";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
