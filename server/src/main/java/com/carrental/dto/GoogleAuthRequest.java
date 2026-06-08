package com.carrental.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for Google OAuth login/registration.
 */
@Data
public class GoogleAuthRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
