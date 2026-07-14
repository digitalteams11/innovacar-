package com.carrental.legal.dto;

import com.carrental.legal.entity.PrivacyRequestStatus;
import com.carrental.legal.entity.PrivacyRequestType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PrivacyRequestDto {
    private Long id;
    private Long userId;
    private String userEmail;
    private Long tenantId;
    private PrivacyRequestType requestType;
    private PrivacyRequestStatus status;
    private String details;
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
}
