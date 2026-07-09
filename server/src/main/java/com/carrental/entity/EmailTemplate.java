package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_template_key_lang",
                columnNames = {"template_key", "language"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable display name shown in the UI. */
    @Column(nullable = false)
    private String name;

    /**
     * Machine key that identifies this template's purpose (e.g. WELCOME_AGENCY).
     * Combined with {@code language} this is unique.
     */
    @Column(name = "template_key", length = 80)
    private String templateKey;

    /** Template category / type label shown in the UI. */
    @Column(nullable = false)
    private String type;

    /** BCP-47 language code: EN | FR | AR. Defaults to EN. */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String language = "EN";

    @Column(nullable = false, length = 300)
    private String subject;

    /** Full HTML body — may include {{variable}} placeholders. */
    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    /** Plain-text fallback — may include {{variable}} placeholders. */
    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    /** JSON array of variable names, kept for legacy UI support. */
    @Column(name = "variables_json", length = 1000)
    private String variablesJson;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /** True for the platform-shipped defaults — prevents permanent deletion. */
    @Column(name = "system_default")
    @Builder.Default
    private Boolean systemDefault = false;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null)        isActive = true;
        if (systemDefault == null)   systemDefault = false;
        if (language == null)        language = "EN";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
