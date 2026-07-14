package com.carrental.legal.dto;

import com.carrental.legal.entity.PrivacyRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrivacyRequestStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private PrivacyRequestStatus status;

    @Size(max = 4000)
    private String resolutionNotes;
}
