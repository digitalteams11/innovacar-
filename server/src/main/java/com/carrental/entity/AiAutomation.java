package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Catalog entry describing an AI-backed automation. {@link #wired} is true
 * only for automations that a real backend flow actually triggers (today:
 * {@code CHAT_ASSISTANT}). All other seeded codes are configurable but inert
 * — the Super Admin can edit prompts/enable them, but no other service in
 * the app calls them yet. This is surfaced to the UI so admins are never
 * misled into thinking a catalog entry is a live feature.
 */
@Entity
@Table(name = "ai_automations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAutomation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "automation_code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "feature_type", length = 60)
    private String featureType;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /** True only for automations wired to real business logic. See class javadoc. */
    @Builder.Default
    @Column(name = "wired", nullable = false)
    private Boolean wired = false;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_prompt_template", columnDefinition = "TEXT")
    private String userPromptTemplate;

    @Column(name = "temperature")
    private BigDecimal temperature;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    /** JSON array of role names allowed to trigger this automation. */
    @Column(name = "allowed_roles", columnDefinition = "TEXT")
    private String allowedRoles;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
