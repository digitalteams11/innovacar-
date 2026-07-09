package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "legal_acceptances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_legal_acceptance_user_document", columnNames = {"user_id", "document_id"})
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalAcceptance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private LegalDocument document;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @PrePersist
    void onCreate() {
        acceptedAt = LocalDateTime.now();
    }
}
