package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelDto {
    private Long id;
    private Long providerId;
    private String modelId;
    private String displayName;
    private Boolean enabled;
    private Boolean defaultModel;
    private Boolean defaultVisionModel;
    private Long contextWindow;
    private BigDecimal inputPricePerMillion;
    private BigDecimal outputPricePerMillion;
    private Boolean supportsStreaming;
    private Boolean supportsJsonMode;
    private Boolean supportsToolCalling;
    private String source;
}
