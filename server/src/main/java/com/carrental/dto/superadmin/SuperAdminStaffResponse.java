package com.carrental.dto.superadmin;

import com.carrental.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Read-only projection of an Innovax platform staff account. */
@Data
@Builder
public class SuperAdminStaffResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Long superAdminRoleId;
    private String superAdminRoleCode;
    private String superAdminRoleLabel;
    private Boolean accountEnabled;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public static SuperAdminStaffResponse from(User user) {
        return SuperAdminStaffResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .superAdminRoleId(user.getSuperAdminRole() != null ? user.getSuperAdminRole().getId() : null)
                .superAdminRoleCode(user.getSuperAdminRole() != null ? user.getSuperAdminRole().getCode() : null)
                .superAdminRoleLabel(user.getSuperAdminRole() != null ? user.getSuperAdminRole().getLabel() : "Unrestricted Super Admin")
                .accountEnabled(user.getAccountEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
