package com.carrental.legal.dto;

import com.carrental.legal.entity.PrivacyRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrivacyRequestCreateRequest {

    @NotNull(message = "Request type is required")
    private PrivacyRequestType requestType;

    @Size(max = 4000)
    private String details;
}
