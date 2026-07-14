package com.carrental.legal.dto;

import lombok.Builder;
import lombok.Data;

/** Super Admin dashboard figure: how many acceptance records exist for one published version. */
@Data
@Builder
public class LegalDocumentVersionStatsDto {
    private Long documentVersionId;
    private Integer versionNumber;
    private String locale;
    private long acceptanceCount;
}
