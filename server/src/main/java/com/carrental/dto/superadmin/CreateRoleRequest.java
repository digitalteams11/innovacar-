package com.carrental.dto.superadmin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoleRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String label;

    private String description;
}
