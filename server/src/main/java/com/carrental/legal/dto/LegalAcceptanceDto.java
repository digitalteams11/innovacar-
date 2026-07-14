package com.carrental.legal.dto;

import com.carrental.legal.entity.LegalDocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LegalAcceptanceDto {
    private Long id;
    private LegalDocumentType documentType;
    private String locale;
    private Integer versionNumber;
    private LocalDateTime acceptedAt;
    private String captureContext;
}
