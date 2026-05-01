package com.carrental.dto.user;

import com.carrental.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code PUT /api/users/{id}} — partial update.
 * Only non-null fields are applied.
 */
@Data
public class UpdateUserRequest {

    /** Change the user's e-mail. Must be unique within the tenant. */
    @Email(message = "Must be a valid email address")
    private String email;

    /** If provided, the password will be re-hashed and replaced. */
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /** Change the user's role. */
    private Role role;
}
