package com.carrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for {@code POST /api/auth/login}.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    private String deviceFingerprint;
    private String deviceName;
    private String otpCode;
    private String recoveryCode;
}
