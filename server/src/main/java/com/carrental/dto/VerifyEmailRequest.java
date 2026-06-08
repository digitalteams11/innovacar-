package com.carrental.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for email verification.
 */
@Data
public class VerifyEmailRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
