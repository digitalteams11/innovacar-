package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@lombok.Data
@Entity
@Table(name = "knowledge_articles")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(length = 500)
    private String summary;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean published;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        if (published == null) published = true;
        updatedAt = LocalDateTime.now();
    }
}
