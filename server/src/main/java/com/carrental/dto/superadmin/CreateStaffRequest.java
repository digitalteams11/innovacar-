package com.carrental.dto.superadmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStaffRequest {

    @NotBlank
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank
    private String firstName;

    private String lastName;

    /** Platform sub-role id. Null = unrestricted (legacy) super admin. */
    private Long superAdminRoleId;
}
