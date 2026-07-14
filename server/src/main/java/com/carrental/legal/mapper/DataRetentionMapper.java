package com.carrental.legal.mapper;

import com.carrental.legal.dto.DataRetentionEntryDto;
import com.carrental.legal.entity.DataRetentionPolicyEntry;

public final class DataRetentionMapper {

    private DataRetentionMapper() {
    }

    public static DataRetentionEntryDto toDto(DataRetentionPolicyEntry entry) {
        if (entry == null) return null;
        return DataRetentionEntryDto.builder()
                .id(entry.getId())
                .dataCategory(entry.getDataCategory())
                .retentionPeriod(entry.getRetentionPeriod())
                .legalBasis(entry.getLegalBasis())
                .displayOrder(entry.getDisplayOrder())
                .build();
    }
}
