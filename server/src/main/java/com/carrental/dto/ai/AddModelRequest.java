package com.carrental.dto.ai;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddModelRequest {
    private String modelId;
    private String displayName;
    private Long contextWindow;
    private BigDecimal inputPricePerMillion;
    private BigDecimal outputPricePerMillion;
    private Boolean supportsStreaming;
    private Boolean supportsJsonMode;
    private Boolean supportsToolCalling;
}
