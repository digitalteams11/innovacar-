package com.carrental.dto.rbac;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateRolePermissionsRequest {
    private List<String> enabledPermissionCodes;
}
