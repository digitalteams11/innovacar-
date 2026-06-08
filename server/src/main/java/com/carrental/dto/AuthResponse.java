package com.carrental.dto;

import com.carrental.entity.Role;
import lombok.Builder;
import lombok.Data;

/**
 * Response body returned by authentication endpoints.
 */
@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;   // always "Bearer"
    private Long expiresIn;     // access token expiry in seconds

    // Embedded user information (convenient for front-end bootstrap)
    private Long   userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role   role;
    private Long   tenantId;
    private String tenantName;
    private Boolean emailVerified;
}
