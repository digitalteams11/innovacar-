package com.carrental.legal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/** Edits a DRAFT in place. Published/archived versions are immutable (create a new draft instead). */
@Data
public class UpdateLegalDocumentVersionRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Content is required")
    private String contentHtml;

    private String summaryOfChanges;

    private boolean material;

    private LocalDate effectiveDate;
}
