package com.carrental.dto.rbac;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateCustomRoleRequest {
    private String code;
    private String name;
    private String description;
    /** System Role this was cloned from — seeds the initial permission set. */
    private String baseTemplate;
    private String color;
    private String icon;
    private List<String> enabledPermissionCodes;
}
