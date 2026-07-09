package com.carrental.dto;

import com.carrental.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

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
    private String roleCode;
    private Long   tenantId;
    private Long   employeeId;
    private List<String> permissions;
    private String tenantName;
    private Boolean emailVerified;
    private Boolean passwordExpired;
    private Boolean mustChangePassword;
    private String verificationStatus;
    private Boolean twoFactorRequired;
    private String twoFactorMethod;
    private String challengeToken;
    /** All 2FA methods available for the challenge (e.g. ["AUTHENTICATOR","EMAIL"]). */
    private Set<String> availableTwoFactorMethods;
    private Boolean emailOtpEnabled;
}
