package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Platform-wide global AI settings (singleton row, Super-Admin managed only).
 * Provider/model/credential configuration now lives on {@link AiProvider} /
 * {@link AiModel} — this row only holds cross-cutting flags and limits that
 * apply regardless of which provider is active.
 */
@Entity
@Table(name = "ai_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "active_provider_id")
    private Long activeProviderId;

    @Column(name = "active_model_id")
    private Long activeModelId;

    @Column(name = "fallback_provider_id")
    private Long fallbackProviderId;

    @Column(name = "fallback_model_id")
    private Long fallbackModelId;

    @Builder.Default
    @Column(name = "fallback_enabled", nullable = false)
    private Boolean fallbackEnabled = false;

    @Builder.Default
    @Column(name = "global_enabled", nullable = false)
    private Boolean globalEnabled = false;

    @Builder.Default
    @Column(name = "temperature")
    private Double temperature = 0.4;

    @Builder.Default
    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens = 4096;

    @Builder.Default
    @Column(name = "request_timeout_seconds")
    private Integer requestTimeoutSeconds = 30;

    @Builder.Default
    @Column(name = "max_retries")
    private Integer maxRetries = 1;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Builder.Default
    @Column(name = "enable_chat", nullable = false)
    private Boolean enableChat = true;

    @Builder.Default
    @Column(name = "enable_reports", nullable = false)
    private Boolean enableReports = true;

    @Builder.Default
    @Column(name = "enable_translations", nullable = false)
    private Boolean enableTranslations = true;

    @Builder.Default
    @Column(name = "enable_support_assistant", nullable = false)
    private Boolean enableSupportAssistant = true;

    @Builder.Default
    @Column(name = "enable_guide_generator", nullable = false)
    private Boolean enableGuideGenerator = true;

    @Builder.Default
    @Column(name = "enable_automation_suggestions", nullable = false)
    private Boolean enableAutomationSuggestions = true;

    @Builder.Default
    @Column(name = "enable_image_generation", nullable = false)
    private Boolean enableImageGeneration = false;

    @Builder.Default
    @Column(name = "monthly_token_limit")
    private Long monthlyTokenLimit = 2_000_000L;

    @Builder.Default
    @Column(name = "daily_request_limit")
    private Integer dailyRequestLimit = 200;

    @Builder.Default
    @Column(name = "audit_all_actions", nullable = false)
    private Boolean auditAllActions = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
