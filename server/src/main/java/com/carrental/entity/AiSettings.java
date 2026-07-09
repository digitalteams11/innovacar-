package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Platform-wide Gemini AI configuration (singleton row, Super-Admin managed
 * only â€” never exposed to tenants). The API key is stored encrypted at rest
 * via {@link com.carrental.util.EncryptionUtil} and is never returned raw by
 * any endpoint.
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

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Builder.Default
    @Column(name = "provider", nullable = false, length = 40)
    private String provider = "GEMINI";

    /** AES-256-GCM encrypted Gemini API key. Never serialized raw. */
    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Builder.Default
    @Column(name = "text_model", length = 80)
    private String textModel = "gemini-1.5-flash";

    @Builder.Default
    @Column(name = "vision_model", length = 80)
    private String visionModel = "gemini-1.5-flash";

    @Builder.Default
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 30;

    @Builder.Default
    @Column(name = "max_tokens")
    private Integer maxTokens = 4096;

    @Builder.Default
    @Column(name = "temperature")
    private Double temperature = 0.4;

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

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @Builder.Default
    @Column(name = "last_test_success")
    private Boolean lastTestSuccess = false;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "last_test_error_code", length = 50)
    private String lastTestErrorCode;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

