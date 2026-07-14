package com.carrental.legal.dto;

import com.carrental.legal.entity.LegalDocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** One document the current user must (re-)accept before continuing — drives a blocking consent modal in the UI. */
@Data
@Builder
public class PendingAcceptanceDto {
    private LegalDocumentType documentType;
    private Long documentVersionId;
    private Integer versionNumber;
    private String locale;
    private String title;
    private String summaryOfChanges;
    private LocalDate effectiveDate;
    /** True if the user never accepted any version of this type before (first-time), false if this is a re-acceptance after a material change. */
    private boolean firstTimeAcceptance;
}
