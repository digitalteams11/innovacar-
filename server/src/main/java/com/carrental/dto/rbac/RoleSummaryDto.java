package com.carrental.dto.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One row in the left-panel role list. */
@Getter
@Builder
@AllArgsConstructor
public class RoleSummaryDto {
    /** "SYSTEM:ADMIN" or "CUSTOM:42" — opaque to the frontend, echoed back on every role-scoped call. */
    private String roleId;
    private String code;
    private String name;
    private String type; // SYSTEM_ROLE | CUSTOM_ROLE
    private String description;
    private String color;
    private String icon;
    private long userCount;
    /** SUPER_ADMIN and built-in codes cannot be renamed/deleted; a custom role can always be edited. */
    private boolean editable;
    private boolean deletable;
}
