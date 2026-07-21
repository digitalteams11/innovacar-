package com.carrental.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code PATCH /api/clients/{id}/email} — the dedicated
 * "add/fix missing client email" action surfaced from Contract Details.
 * Unlike {@link UpdateClientRequest}, email here is required: this endpoint
 * exists specifically to set a real address, not to clear one.
 */
@Data
public class UpdateClientEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;
}
