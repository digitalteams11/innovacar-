package com.carrental.legal.mapper;

import com.carrental.legal.dto.PrivacyRequestDto;
import com.carrental.legal.entity.PrivacyRequest;

public final class PrivacyRequestMapper {

    private PrivacyRequestMapper() {
    }

    public static PrivacyRequestDto toDto(PrivacyRequest request, String userEmail) {
        if (request == null) return null;
        return PrivacyRequestDto.builder()
                .id(request.getId())
                .userId(request.getUserId())
                .userEmail(userEmail)
                .tenantId(request.getTenantId())
                .requestType(request.getRequestType())
                .status(request.getStatus())
                .details(request.getDetails())
                .requestedAt(request.getRequestedAt())
                .resolvedAt(request.getResolvedAt())
                .resolutionNotes(request.getResolutionNotes())
                .build();
    }
}
