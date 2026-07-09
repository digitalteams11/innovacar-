package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@lombok.Data
@Entity
@Table(name = "legal_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_legal_document_code_version", columnNames = {"code", "version"})
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String version;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @PrePersist
    void onCreate() {
        if (active == null) active = true;
        if (publishedAt == null) publishedAt = LocalDateTime.now();
    }
}
