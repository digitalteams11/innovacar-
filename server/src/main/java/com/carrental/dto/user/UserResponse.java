package com.carrental.dto.user;

import com.carrental.entity.Role;
import com.carrental.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * Read-only user projection returned by all user management endpoints.
 * Password is intentionally excluded.
 */
@Data
@Builder
public class UserResponse {

    private Long   id;
    private String email;
    private Role   role;
    private Long   tenantId;
    private String tenantName;

    // ── Static factory ───────────────────────────────────────────────────────

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenant().getId())
                .tenantName(user.getTenant().getName())
                .build();
    }
}
