package com.carrental.dto.superadmin;

import lombok.Data;

@Data
public class UpdateStaffRequest {
    private String firstName;
    private String lastName;
    private Long superAdminRoleId;
}
