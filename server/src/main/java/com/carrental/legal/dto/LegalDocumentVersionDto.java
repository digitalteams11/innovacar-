package com.carrental.legal.dto;

import com.carrental.legal.entity.LegalDocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Full document version content, for public reading and for the Super Admin editor. */
@Data
@Builder
public class LegalDocumentVersionDto {
    private Long id;
    private LegalDocumentType documentType;
    private String locale;
    private Integer versionNumber;
    private String title;
    private String contentHtml;
    private String summaryOfChanges;
    private boolean material;
    private String status;
    private LocalDate effectiveDate;
    private LocalDateTime publishedAt;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
