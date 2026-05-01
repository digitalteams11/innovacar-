package com.carrental.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/auth/signup}.
 * Creates a new tenant + an admin user in a single atomic operation.
 */
@Data
public class SignupRequest {

    // ── Tenant fields ────────────────────────────────────────────────────────

    @NotBlank(message = "Tenant name is required")
    @Size(min = 2, max = 100)
    private String tenantName;

    @NotBlank(message = "Tenant email is required")
    @Email(message = "Must be a valid email address")
    private String tenantEmail;

    /** Optional — defaults to 1 year from today if omitted */
    private LocalDate subscriptionEndDate;

    // ── Admin-user fields ────────────────────────────────────────────────────

    @NotBlank(message = "Admin email is required")
    @Email(message = "Must be a valid email address")
    private String adminEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
