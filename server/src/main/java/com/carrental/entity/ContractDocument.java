package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Document checklist item for a rental contract.
 */
@Entity
@Table(name = "contract_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "document_name", length = 150)
    private String documentName;

    @Column(name = "document_url", length = 512)
    private String documentUrl;

    @Column(name = "is_present")
    private Boolean isPresent = false;

    @Column(name = "verified_at")
    private java.time.LocalDateTime verifiedAt;

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    // ── Link ─────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;
}
