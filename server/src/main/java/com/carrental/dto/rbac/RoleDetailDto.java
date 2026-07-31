package com.carrental.dto.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RoleDetailDto {
    private String roleId;
    private String code;
    private String name;
    private String description;
    private String type;
    private boolean editable;
    private List<PermissionStateDto> permissions;
}
