package com.carrental.dto.rbac;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionOverrideRequest {
    private String permissionCode;
    /** GRANT | DENY */
    private String overrideType;
    private String reason;
}
