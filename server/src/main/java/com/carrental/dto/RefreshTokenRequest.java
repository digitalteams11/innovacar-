package com.carrental.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for token refresh.
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
