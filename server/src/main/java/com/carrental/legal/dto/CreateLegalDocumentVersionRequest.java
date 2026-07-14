package com.carrental.legal.dto;

import com.carrental.legal.entity.LegalDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** Creates a new DRAFT version — the next sequential version number for (documentType, locale) is assigned server-side. */
@Data
public class CreateLegalDocumentVersionRequest {

    @NotNull(message = "Document type is required")
    private LegalDocumentType documentType;

    @NotBlank(message = "Locale is required")
    private String locale;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Content is required")
    private String contentHtml;

    private String summaryOfChanges;

    private boolean material;

    private LocalDate effectiveDate;
}
