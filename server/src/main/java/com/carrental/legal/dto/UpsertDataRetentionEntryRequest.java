package com.carrental.legal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpsertDataRetentionEntryRequest {

    @NotBlank(message = "Data category is required")
    private String dataCategory;

    @NotBlank(message = "Retention period is required")
    private String retentionPeriod;

    private String legalBasis;

    private Integer displayOrder;
}
