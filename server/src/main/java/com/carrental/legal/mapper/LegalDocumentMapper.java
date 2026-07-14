package com.carrental.legal.mapper;

import com.carrental.legal.dto.LegalDocumentSummaryDto;
import com.carrental.legal.dto.LegalDocumentVersionDto;
import com.carrental.legal.entity.LegalDocumentVersion;

public final class LegalDocumentMapper {

    private LegalDocumentMapper() {
    }

    public static LegalDocumentVersionDto toDto(LegalDocumentVersion version) {
        if (version == null) return null;
        return LegalDocumentVersionDto.builder()
                .id(version.getId())
                .documentType(version.getDocumentType())
                .locale(version.getLocale().name())
                .versionNumber(version.getVersionNumber())
                .title(version.getTitle())
                .contentHtml(version.getContentHtml())
                .summaryOfChanges(version.getSummaryOfChanges())
                .material(version.isMaterial())
                .status(version.getStatus().name())
                .effectiveDate(version.getEffectiveDate())
                .publishedAt(version.getPublishedAt())
                .createdByEmail(version.getCreatedByEmail())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .build();
    }

    public static LegalDocumentSummaryDto toSummaryDto(LegalDocumentVersion version) {
        if (version == null) return null;
        return LegalDocumentSummaryDto.builder()
                .documentType(version.getDocumentType())
                .locale(version.getLocale().name())
                .versionNumber(version.getVersionNumber())
                .title(version.getTitle())
                .effectiveDate(version.getEffectiveDate())
                .material(version.isMaterial())
                .build();
    }
}
