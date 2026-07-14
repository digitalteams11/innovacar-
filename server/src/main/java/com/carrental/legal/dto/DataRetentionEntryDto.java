package com.carrental.legal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataRetentionEntryDto {
    private Long id;
    private String dataCategory;
    private String retentionPeriod;
    private String legalBasis;
    private Integer displayOrder;
}
