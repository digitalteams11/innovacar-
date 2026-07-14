package com.carrental.legal.mapper;

import com.carrental.legal.dto.LegalAcceptanceDto;
import com.carrental.legal.entity.LegalAcceptance;

public final class LegalAcceptanceMapper {

    private LegalAcceptanceMapper() {
    }

    public static LegalAcceptanceDto toDto(LegalAcceptance acceptance) {
        if (acceptance == null) return null;
        return LegalAcceptanceDto.builder()
                .id(acceptance.getId())
                .documentType(acceptance.getDocumentType())
                .locale(acceptance.getLocale().name())
                .versionNumber(acceptance.getVersionNumber())
                .acceptedAt(acceptance.getAcceptedAt())
                .captureContext(acceptance.getCaptureContext())
                .build();
    }
}
