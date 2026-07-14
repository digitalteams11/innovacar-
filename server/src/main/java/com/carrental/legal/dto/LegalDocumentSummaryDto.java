package com.carrental.legal.dto;

import com.carrental.legal.entity.LegalDocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** Lightweight listing entry — one per document type, showing its currently published version per locale. */
@Data
@Builder
public class LegalDocumentSummaryDto {
    private LegalDocumentType documentType;
    private String locale;
    private Integer versionNumber;
    private String title;
    private LocalDate effectiveDate;
    private boolean material;
}
