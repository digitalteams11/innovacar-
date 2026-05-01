package com.carrental.dto;

import com.carrental.entity.Role;
import lombok.Builder;
import lombok.Data;

/**
 * Response body returned by both signup and login endpoints.
 */
@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String tokenType;   // always "Bearer"

    // Embedded user information (convenient for front-end bootstrap)
    private Long   userId;
    private String email;
    private Role   role;
    private Long   tenantId;
    private String tenantName;
}
