package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One configured AI provider instance (Groq, Gemini, OpenAI, OpenRouter, or a
 * custom OpenAI-compatible endpoint). The API key is stored encrypted at rest
 * via {@link com.carrental.service.AiCredentialEncryptionService} and is
 * never returned raw by any endpoint — only {@link #apiKeyMaskedHint}.
 */
@Entity
@Table(name = "ai_providers", indexes = {
        @Index(name = "idx_ai_providers_type", columnList = "provider_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 40)
    private AiProviderType providerType;

    /** Nullable for built-in providers (defaults used); required for CUSTOM_OPENAI_COMPATIBLE. */
    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    /** Non-secret display hint, e.g. "gsk_••••••••••••x92A". */
    @Column(name = "api_key_masked_hint", length = 60)
    private String apiKeyMaskedHint;

    @Column(name = "organization_id", length = 120)
    private String organizationId;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Builder.Default
    @Column(name = "is_fallback", nullable = false)
    private Boolean isFallback = false;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 30)
    private AiConnectionStatus connectionStatus = AiConnectionStatus.NOT_TESTED;

    @Column(name = "last_connection_test_at")
    private LocalDateTime lastConnectionTestAt;

    @Column(name = "last_connection_error", columnDefinition = "TEXT")
    private String lastConnectionError;

    @Column(name = "last_test_latency_ms")
    private Long lastTestLatencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

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
