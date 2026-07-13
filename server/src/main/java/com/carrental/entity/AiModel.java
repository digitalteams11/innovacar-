package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_models", indexes = {
        @Index(name = "idx_ai_models_provider", columnList = "ai_provider_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_provider_id", nullable = false)
    private Long providerId;

    @Column(name = "model_id", nullable = false, length = 120)
    private String modelId;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "default_model", nullable = false)
    private Boolean defaultModel = false;

    @Builder.Default
    @Column(name = "default_vision_model", nullable = false)
    private Boolean defaultVisionModel = false;

    @Column(name = "context_window")
    private Long contextWindow;

    @Column(name = "input_price_per_million")
    private BigDecimal inputPricePerMillion;

    @Column(name = "output_price_per_million")
    private BigDecimal outputPricePerMillion;

    @Builder.Default
    @Column(name = "supports_streaming", nullable = false)
    private Boolean supportsStreaming = false;

    @Builder.Default
    @Column(name = "supports_json_mode", nullable = false)
    private Boolean supportsJsonMode = false;

    @Builder.Default
    @Column(name = "supports_tool_calling", nullable = false)
    private Boolean supportsToolCalling = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AiModelSource source = AiModelSource.MANUAL;

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
